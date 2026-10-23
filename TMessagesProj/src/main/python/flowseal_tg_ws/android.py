"""Android lifecycle adapter; transport modules are unmodified upstream sources."""
import asyncio
import logging
import threading

from . import tg_ws_proxy as core
from .config import proxy_config
from .stats import stats as counters

_thread = None
_loop = None
_stop_event = None
_error = ''
_lock = threading.RLock()


class AndroidLog(logging.Handler):
    def emit(self, record):
        try:
            from java import jclass
            # Upstream startup logs contain the local secret: do not forward them.
            message = self.format(record)
            if 'secret' not in message.lower() and 'tg://' not in message and 't.me/' not in message:
                jclass('android.util.Log').i('FlowsealTgWs', message)
        except Exception:
            pass


_log = logging.getLogger('tg-mtproto-proxy')
_log.setLevel(logging.INFO)
_log.addHandler(AndroidLog())


def start(host, port, secret):
    global _thread, _error
    with _lock:
        stop()
        if _thread is not None and _thread.is_alive():
            raise RuntimeError('Previous Flowseal proxy is still stopping')
        proxy_config.host = host
        proxy_config.port = int(port)
        proxy_config.secret = secret.removeprefix('dd') if len(secret) == 34 else secret
        _error = ''
        ready = threading.Event()

        def run():
            global _loop, _stop_event, _error
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            _loop = loop
            _stop_event = asyncio.Event()

            async def serve():
                task = asyncio.create_task(core._run(_stop_event))
                try:
                    while core._server_instance is None and not task.done():
                        await asyncio.sleep(.02)
                    if task.done():
                        await task
                    ready.set()
                    stop_waiter = asyncio.create_task(_stop_event.wait())
                    try:
                        await asyncio.wait((task, stop_waiter), return_when=asyncio.FIRST_COMPLETED)
                        if not task.done():
                            try:
                                await asyncio.wait_for(asyncio.shield(task), 2)
                            except asyncio.TimeoutError:
                                task.cancel()
                        await asyncio.gather(task, return_exceptions=True)
                    finally:
                        stop_waiter.cancel()
                        await asyncio.gather(stop_waiter, return_exceptions=True)
                finally:
                    if not task.done():
                        task.cancel()
                        await asyncio.gather(task, return_exceptions=True)
                    server = core._server_instance
                    if server is not None:
                        server.close()
                        await server.wait_closed()
                    await core.ws_pool.close()
                    await core.cf_worker_pool.close()
                    if core.cf_h2_pool is not None:
                        await core.cf_h2_pool.close()
                        core.cf_h2_pool = None
                    core._server_instance = None
            try:
                loop.run_until_complete(serve())
            except Exception as exc:
                _error = str(exc)
                _log.error('Proxy stopped: %s', exc)
            finally:
                ready.set()
                pending = asyncio.all_tasks(loop)
                for task in pending:
                    task.cancel()
                loop.run_until_complete(asyncio.gather(*pending, return_exceptions=True))
                loop.run_until_complete(loop.shutdown_asyncgens())
                loop.close()
                _loop = None
                _stop_event = None

        _thread = threading.Thread(target=run, name='flowseal-tg-ws', daemon=True)
        _thread.start()
        if not ready.wait(10):
            stop()
            raise RuntimeError('Flowseal listener startup timed out')
        if _error or not _thread.is_alive():
            raise RuntimeError(_error or 'Flowseal listener stopped')
        return 0


def stop():
    with _lock:
        loop, event, thread = _loop, _stop_event, _thread
        if loop is not None and event is not None and not loop.is_closed():
            loop.call_soon_threadsafe(event.set)
        if thread is not None and thread is not threading.current_thread():
            thread.join(5)


def stats():
    if _thread is None or not _thread.is_alive():
        return ''
    return counters.summary()


def set_pool_size(size):
    proxy_config.pool_size = max(0, int(size))


def set_secret(secret):
    proxy_config.secret = secret


def set_cf_proxy(enabled, domain):
    from .config import coerce_domain_list
    proxy_config.fallback_cfproxy = bool(enabled)
    proxy_config.cfproxy_user_domains = coerce_domain_list(domain)


def secret_with_prefix():
    return 'dd' + proxy_config.secret
