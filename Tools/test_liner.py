#!/usr/bin/env python3
"""Offline Liner HTTP/SSE, catalog and stalled-worker regression checks (JDK 17).

Pass --json-jar pointing to an org.json JAR. No account, Android SDK or network
is used; platform storage/WebView are faked, production API/catalog code is compiled.
"""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PLAYER = ROOT / 'TMessagesProj/src/main/java/app/exteraless/player'
STUBS = {
    'android/text/TextUtils.java': '''package android.text; public class TextUtils {
 public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} }''',
    'android/util/LruCache.java': '''package android.util; public class LruCache<K,V> {
 private final java.util.Map<K,V> values=new java.util.HashMap<>(); public LruCache(int size){}
 public synchronized V get(K k){return values.get(k);} public synchronized V put(K k,V v){return values.put(k,v);}
 public synchronized V remove(K k){return values.remove(k);} public synchronized void evictAll(){values.clear();} }''',
    'android/os/SystemClock.java': '''package android.os; public class SystemClock {
 public static long elapsedRealtime(){return System.nanoTime()/1000000;} }''',
    'android/util/Log.java': '''package android.util; public class Log {
 public static int i(String t,String m){return 0;} public static int w(String t,String m){return 0;} }''',
    'android/util/Base64.java': '''package android.util; public class Base64 {
 public static final int NO_WRAP=2,DEFAULT=0;
 public static byte[] decode(String s,int f){return java.util.Base64.getDecoder().decode(s);}
 public static String encodeToString(byte[] s,int f){return java.util.Base64.getEncoder().encodeToString(s);} }''',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
 public class ApplicationLoader { public static final Context applicationContext=new Context();
 public static class Context {
 public java.io.File getCacheDir(){return new java.io.File(System.getProperty("java.io.tmpdir"));}
 public java.io.File getNoBackupFilesDir(){throw new AssertionError("Unexpected credential access");}} }''',
    'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;
 public class AndroidUtilities {public static void runOnUIThread(Runnable task){task.run();}}''',
    'org/telegram/messenger/DispatchQueue.java': '''package org.telegram.messenger;
 public class DispatchQueue {public DispatchQueue(String name){} public void postRunnable(Runnable task){task.run();}}''',
    'org/telegram/messenger/BuildVars.java': '''package org.telegram.messenger;
 public class BuildVars {public static final String BUILD_VERSION_STRING="test";}''',
    'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;
 public class FileLog {public static void e(String message,Throwable error){} public static void e(Throwable error){}}''',
    'org/telegram/messenger/Utilities.java': '''package org.telegram.messenger;
 public class Utilities {public static String MD5(String s){return "test-query";}}''',
    'app/exteraless/player/LinerLyricsParser.java': '''package app.exteraless.player;
 class LinerLyricsParser {static Lyrics parse(String s,String f,String p,long d){return Lyrics.parse(s,Lyrics.SOURCE_ONLINE,p);}}''',
    'app/exteraless/player/WordLyricsParser.java': '''package app.exteraless.player;
 class WordLyricsParser {static Lyrics parseStructured(String s,int source,String provider){return null;}}''',
    'app/exteraless/player/LinerCheck.java': r'''package app.exteraless.player;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class LinerCheck {
 static int checks;
 static String stream;
 static int lyricsStatus=200;
 static final List<String> requests=Collections.synchronizedList(new ArrayList<>());
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static void setup()throws Exception {
  URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol)?new URLStreamHandler(){
   protected URLConnection openConnection(URL url){return new HttpURLConnection(url){
    public void disconnect(){} public boolean usingProxy(){return false;} public void connect(){}
    public OutputStream getOutputStream(){return new ByteArrayOutputStream();}
    public int getResponseCode(){requests.add(url.toString());return url.getPath().endsWith("/lyrics")?lyricsStatus:200;}
    public InputStream getInputStream(){
     String body=url.getPath().endsWith("/lyrics")?stream:
      "{\"items\":[{\"type\":\"track\",\"id\":\"jealous-id\",\"title\":\"Jealous\",\"durationMs\":180000,\"artists\":[{\"name\":\"9mice\"},{\"name\":\"Егор Крид\"},{\"name\":\"тёмный принц\"},{\"name\":\"madk1d\"}]}]}";
     if(!url.getHost().equals("api.tryliner.fun"))body=url.getHost().equals("lrclib.net")&&url.getPath().endsWith("/search")?"[]":"{}";
     if(url.getHost().equals("music.youtube.com"))body=musicCatalog();
     return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }
   };}
  }:null);
  Field session=LinerApi.class.getDeclaredField("session"); session.setAccessible(true);
  session.set(null,new JSONObject().put("accessToken","fixture-access").put("refreshToken","fixture-refresh"));
 }
 static String musicCatalog()throws RuntimeException {
  JSONArray artists=new JSONArray();
  for(String artist:new String[]{"9mice","Егор Крид","тёмный принц","madk1d"}) {
   JSONObject browse=new JSONObject().put("browseEndpointContextSupportedConfigs",new JSONObject()
    .put("browseEndpointContextMusicConfig",new JSONObject().put("pageType","MUSIC_PAGE_TYPE_ARTIST")));
   artists.put(new JSONObject().put("text",artist).put("navigationEndpoint",new JSONObject().put("browseEndpoint",browse)));
  }
  JSONObject renderer=new JSONObject().put("playlistItemData",new JSONObject().put("videoId","jealous-id"))
   .put("flexColumns",new JSONArray()
    .put(new JSONObject().put("musicResponsiveListItemFlexColumnRenderer",new JSONObject().put("text",new JSONObject().put("runs",new JSONArray().put(new JSONObject().put("text","Jealous"))))))
    .put(new JSONObject().put("musicResponsiveListItemFlexColumnRenderer",new JSONObject().put("text",new JSONObject().put("runs",artists)))))
   .put("fixedColumns",new JSONArray().put(new JSONObject().put("musicResponsiveListItemFixedColumnRenderer",new JSONObject()
    .put("text",new JSONObject().put("runs",new JSONArray().put(new JSONObject().put("text","3:00")))))));
  return new JSONObject().put("musicResponsiveListItemRenderer",renderer).toString();
 }
 static String event(String json){return "data: "+json+"\n\n";}
 static String found(String provider){return event("{\"type\":\"provider\",\"provider\":\""+provider+"\",\"status\":\"found\",\"candidate\":{\"provider\":\""+provider+"\",\"syncLevel\":\"line_level\",\"lyrics\":{\"format\":\"lrc\",\"content\":\"[00:01.00]Jealous\"}}}");}
 static String done(){return event("{\"type\":\"done\"}");}
 static OnlineLyrics.Query query(){return new OnlineLyrics.Query("9mice, Егор Крид, тёмный принц, madk1d","Jealous",null,180);}
 static void streams()throws Exception {
  stream=found("first")+found("second")+done();
  List<Lyrics> updates=new ArrayList<>();
  List<Lyrics> result=LinerApi.lyrics(query(),updates::add);
  check(result.size()==2&&updates.size()==2,"both streamed Liner providers survive");
  check(result.get(0).provider.equals("Liner · first"),"provider is shown as Liner");
  check(requests.stream().anyMatch(s->s.contains("/tracks/jealous-id/lyrics")),"Jealous uses the matched catalog ID");
  check(requests.stream().noneMatch(s->s.contains("api.tryliner.fun/v1/search")),"removed Liner search endpoint is never used");
  stream=event("{\"type\":\"provider\",\"provider\":\"first\",\"status\":\"not_found\"}")+done();
  check(LinerApi.lyrics(query()).isEmpty(),"legitimate not-found completes normally");
  stream=event("{\"type\":\"provider\",\"provider\":\"first\",\"status\":\"error\"}")+done();
  expectFailure("provider errors must not become a cached not-found");
  stream=found("first");updates.clear();
  try {LinerApi.lyrics(query(),updates::add);throw new AssertionError("truncated stream accepted");}
  catch(IOException expected){check(updates.size()==1,"completed provider retained after truncation");}
  stream="";expectFailure("empty truncated response is not a completed search");
  stream=event("{invalid}")+done();expectFailure("malformed provider response is not a completed search");
  lyricsStatus=403;expectFailure("access denial is not a not-found result");lyricsStatus=200;
 }
 static void expectFailure(String label)throws Exception {
  try {LinerApi.lyrics(query());throw new AssertionError(label);}
  catch(IOException expected){check(true,label);}
 }
 static void cache()throws Exception {
  OnlineLyrics.clearSourceCache();
  stream=found("retained")+event("{\"type\":\"provider\",\"provider\":\"missing\",\"status\":\"error\"}")+done();
  CompletableFuture<Integer> incomplete=new CompletableFuture<>();
  List<Lyrics> received=Collections.synchronizedList(new ArrayList<>());
  OnlineLyrics.fetch(query(),(sources,status)->{
   if(sources!=null)received.addAll(sources);
   if(status!=OnlineLyrics.OK)incomplete.complete(status);
  });
  check(incomplete.get(3,TimeUnit.SECONDS)==OnlineLyrics.PARTIAL,"provider error is reported as partial rather than success");
  check(received.stream().anyMatch(l->l.provider.equals("Liner · retained")),"partial stream still displays completed Liner provider");
  check(OnlineLyrics.cached(query())==null,"incomplete Liner sources are not cached as a complete search");
  stream=found("recovered")+done();
  CountDownLatch updates=new CountDownLatch(2);
  OnlineLyrics.fetch(query(),(sources,status)->updates.countDown());
  check(updates.await(3,TimeUnit.SECONDS),"retry reaches Liner instead of hitting incomplete cache");
  List<Lyrics> cached=OnlineLyrics.cached(query());
  check(cached!=null&&cached.get(0).provider.equals("Liner · recovered"),"completed retry caches recovered Liner provider");
 }
 static void stall(CountDownLatch started,CountDownLatch release){
  started.countDown();boolean interrupted=false;
  while(true)try{release.await();break;}catch(InterruptedException ignored){interrupted=true;}
  if(interrupted)Thread.currentThread().interrupt();
 }
 static void workers()throws Exception {
  CountDownLatch started=new CountDownLatch(5),release=new CountDownLatch(1);
  List<Future<?>> old=new ArrayList<>();
  try {
   for(int i=0;i<5;i++)old.add(LyricsWorkers.submit(false,()->stall(started,release)));
   check(started.await(2,TimeUnit.SECONDS),"old public providers started");
   for(Future<?> task:old)task.cancel(true);
   CountDownLatch linerStarted=new CountDownLatch(1);
   Future<?> liner=LyricsWorkers.submit(true,()->stall(linerStarted,release));old.add(liner);
   check(linerStarted.await(2,TimeUnit.SECONDS),"old Liner request started");liner.cancel(true);
   check(LyricsWorkers.submit(true,()->{}).get(2,TimeUnit.SECONDS)==null,"new Liner track starts despite cancelled stuck requests");
   check(LyricsWorkers.submit(false,()->{}).get(2,TimeUnit.SECONDS)==null,"new public providers start despite cancelled stuck requests");
   CountDownLatch full=new CountDownLatch(1);
   old.add(LyricsWorkers.submit(true,()->stall(full,release)));
   check(full.await(2,TimeUnit.SECONDS),"Liner worker limit reached");
   try {LyricsWorkers.submit(true,()->{});throw new AssertionError("queued behind stuck sockets");}
   catch(RejectedExecutionException expected){check(true,"overload fails promptly instead of queueing indefinitely");}
   LyricsDeadline deadline=new LyricsDeadline(60);deadline.cancelAsync();
   check(!deadline.active,"track switch immediately invalidates provider callbacks");
  } finally {release.countDown();for(Future<?> task:old)task.cancel(true);}
 }
 public static void main(String[] args)throws Exception {
  try {setup();streams();cache();workers();System.out.println("Liner: "+checks+" checks passed");System.exit(0);}
  catch(Throwable error){error.printStackTrace();System.exit(1);}
 }
}''',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--json-jar', type=Path, required=True)
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='linkigram-liner-test-') as directory:
        temp = Path(directory)
        for name, source in STUBS.items():
            target = temp / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(source, encoding='utf-8')
        for name in ['android/util/AtomicFile.java', 'android/security/keystore/KeyGenParameterSpec.java',
                     'android/security/keystore/KeyProperties.java']:
            target = temp / name
            target.parent.mkdir(parents=True, exist_ok=True)
            source = (ROOT / 'Tools/tests/ai_config' / name).read_text()
            if name.endswith('/AtomicFile.java'):
                source = source.replace('public class AtomicFile {', 'public class AtomicFile { public void delete(){getBaseFile().delete();}')
            target.write_text(source, encoding='utf-8')
        sources = [str(p) for p in temp.rglob('*.java')]
        sources += [str(PLAYER / name) for name in ['LinerApi.java', 'LinerCatalog.java', 'Lyrics.java',
                                                   'LyricsDeadline.java', 'LyricsWorkers.java', 'OnlineLyrics.java']]
        subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(args.json_jar), '-d', str(temp/'classes'), *sources], check=True)
        subprocess.run(['java', '-ea', '-Djava.io.tmpdir=' + str(temp), '-cp', str(temp/'classes') + ':' + str(args.json_jar),
                        'app.exteraless.player.LinerCheck'], check=True, timeout=20)


if __name__ == '__main__':
    main()
