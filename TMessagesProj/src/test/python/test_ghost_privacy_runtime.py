import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
GHOST = ROOT / 'TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuGhostUtils.java'
CONNECTIONS = ROOT / 'TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class GhostPrivacyTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_real_interceptor_and_offline_callback(self):
        source = GHOST.read_text(encoding='utf-8')
        signatures = ['public static InterceptResult interceptRequest(int account',
                      'private static RequestDelegate handleOfflineAfterSend(int account',
                      'private static boolean isMessageSendRequest(TLObject object)']
        methods = '\n'.join(method(source, signature) for signature in signatures)
        methods = methods.replace('app.nimarkogram.messenger.NimarkoConfig', 'NimarkoConfig')
        harness = '''import java.util.*;
public class Main {
 static class TLObject {} static class Error {} interface RequestDelegate {void run(TLObject r, Error e);}
 static class TLRPC {
  static class InputPeer {} static class Bool extends TLObject {} static class TL_boolTrue extends Bool {}
  static class TL_messages_setTyping extends TLObject {Object action; InputPeer peer;}
  static class TL_messages_setEncryptedTyping extends TLObject {boolean typing;}
  static class TL_sendMessageRecordAudioAction {} static class TL_sendMessageRecordRoundAction {} static class TL_sendMessageRecordVideoAction {} static class TL_sendMessageCancelAction {}
  static class TL_messages_getMessagesViews extends TLObject {boolean increment;}
  static class TL_messages_sendMessage extends TLObject {} static class TL_messages_sendMedia extends TLObject {} static class TL_messages_sendMultiMedia extends TLObject {}
  static class TL_messages_forwardMessages extends TLObject {} static class TL_messages_sendInlineBotResult extends TLObject {}
  static class TL_messages_sendEncrypted extends TLObject {} static class TL_messages_sendEncryptedFile extends TLObject {} static class TL_messages_sendEncryptedMultiMedia extends TLObject {} static class TL_messages_sendEncryptedService extends TLObject {}
 }
 static class TL_account {static class updateStatus extends TLObject {boolean offline;}}
 static class NimarkoConfig {static boolean ghostTyping,ghostRecording,ghostOnline,ghostReadReceipts,ghostStories,hideStoryViews,ghostOfflineAfterSend;}
 static class AyuGhostPreferences {static boolean exclude; static boolean isTypingExcluded(int a,long d){return exclude;} static boolean isReadExcluded(int a,long d){return false;}}
 static class AyuState {static boolean getAllowReadPacket(){return false;}}
 static class FileLog {static void d(String s){}}
 static class Queue {List<Runnable> tasks=new ArrayList<>(); void postRunnable(Runnable r){tasks.add(r);} void postRunnable(Runnable r,int delay){tasks.add(r);} void flush(){while(!tasks.isEmpty()) tasks.remove(0).run();}}
 static class Utilities {static Queue stageQueue=new Queue(), globalQueue=new Queue();}
 static class AyuGhostUtils {static Long getDialogId(TLRPC.InputPeer p){return 9L;}}
 static Set<TLObject> bypassed=new HashSet<>(); static Map<TLObject,Long> requestDialogs=new HashMap<>(); static boolean storyGhostSession; static final int OFFLINE_DELAY_MS=1000; static int offlineAccount=-1;
 static Long getDialogId(TLRPC.InputPeer p){return 9L;} static Long extractDialogId(TLObject o){return 9L;} static boolean isReadMessageRequest(TLObject o){return false;} static boolean isReadStoriesRequest(TLObject o){return false;}
 static void sendFakeReadResponse(RequestDelegate d){} static void handleReadAfterSend(int a,TLObject o){} static TLRPC.InputPeer extractPeerFromSendObject(TLObject o){return null;}
 static void performStatusRequest(int account,boolean offline){if(offline) offlineAccount=account;}
 record InterceptResult(boolean blockRequest,RequestDelegate effectiveOnComplete){static InterceptResult Blocked(RequestDelegate d){return new InterceptResult(true,d);} static InterceptResult Proceed(RequestDelegate d){return new InterceptResult(false,d);}}
 ''' + methods + '''
 public static void main(String[] args){
  NimarkoConfig.ghostTyping=true; NimarkoConfig.ghostOnline=true;
  TLRPC.TL_messages_setTyping typing=new TLRPC.TL_messages_setTyping(); final int[] calls={0};
  InterceptResult blocked=interceptRequest(2,typing,(r,e)->{if(!(r instanceof TLRPC.TL_boolTrue)) throw new AssertionError(); calls[0]++;});
  if(!blocked.blockRequest()) throw new AssertionError("typing not blocked"); Utilities.stageQueue.flush(); if(calls[0]!=1) throw new AssertionError("callback");
  typing.action=new TLRPC.TL_sendMessageCancelAction(); if(interceptRequest(2,typing,null).blockRequest()) throw new AssertionError("cancel blocked");
  typing.action=null; AyuGhostPreferences.exclude=true; if(interceptRequest(2,typing,null).blockRequest()) throw new AssertionError("exception blocked"); AyuGhostPreferences.exclude=false;
  TL_account.updateStatus status=new TL_account.updateStatus(); interceptRequest(2,status,null); if(!status.offline) throw new AssertionError("online leaked");
  InterceptResult sent=interceptRequest(3,new TLRPC.TL_messages_forwardMessages(),(r,e)->calls[0]++); sent.effectiveOnComplete().run(null,null); Utilities.stageQueue.flush(); Utilities.globalQueue.flush(); if(offlineAccount!=3 || calls[0]!=2) throw new AssertionError("wrong account or callback");
  offlineAccount=-1; sent=interceptRequest(4,new TLRPC.TL_messages_sendEncrypted(),null); sent.effectiveOnComplete().run(null,null); NimarkoConfig.ghostOnline=false; Utilities.globalQueue.flush(); if(offlineAccount!=-1) throw new AssertionError("offline after disabling");
 }
}'''
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'Main.java'
            path.write_text(harness, encoding='utf-8')
            javac = pathlib.Path(shutil.which('javac')).resolve()
            java = javac.with_name('java.exe' if os.name == 'nt' else 'java')
            subprocess.run([str(javac), '-encoding', 'UTF-8', str(path)], check=True)
            subprocess.run([str(java), '-cp', directory, 'Main'], check=True)

    def test_filter_runs_after_plugin_replacement(self):
        source = CONNECTIONS.read_text(encoding='utf-8')
        body = method(source, 'private void sendRequestInternal(')
        self.assertLess(body.index('executePreRequestHook'), body.index('interceptRequest(currentAccount, hookedObject'))
        self.assertLess(body.index('interceptRequest(currentAccount, hookedObject'), body.index('object.serializeToStream'))


if __name__ == '__main__':
    unittest.main()
