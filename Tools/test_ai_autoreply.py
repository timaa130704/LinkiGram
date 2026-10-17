#!/usr/bin/env python3
"""Compile the real AutoReplyController against deterministic transport/platform fakes.

Requires JDK 17 and an org.json jar (--json-jar, or Gradle's existing cache).
Does not invoke Gradle or use the network; all generated files live in TemporaryDirectory.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long elapsedRealtime(){ return System.nanoTime()/1000000; }}',
'org/telegram/tgnet/ConnectionsManager.java': 'package org.telegram.tgnet; public class ConnectionsManager { public static int now=10000; public static ConnectionsManager getInstance(int a){return new ConnectionsManager();} public int getCurrentTime(){return now;} }',
'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet; public class TLRPC {
 public static class Message { public int id,date; public String message="hello"; public Object action; public long via_bot_id; }
 public static class TL_message extends Message {} public static class TL_messageService extends Message {}
 public static class User {public long id=20; public boolean bot,deleted;}
 public static class Chat {public boolean forum,monoforum;}
}''',
'org/telegram/messenger/MessageObject.java': '''package org.telegram.messenger; import org.telegram.tgnet.TLRPC;
public class MessageObject { public TLRPC.Message messageOwner; public boolean out; public long sender=20; public int currentAccount;
 public boolean isOut(){return out;} public int getId(){return messageOwner.id;} public long getSenderId(){return sender;} }''',
'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger; public class UserConfig { public static boolean active=true; public static long user=10;
 public static UserConfig getInstance(int a){return new UserConfig();} public boolean isClientActivated(){return active;} public long getClientUserId(){return user;} }''',
'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class MessagesController {
 public static TLRPC.User sender=new TLRPC.User(); public static TLRPC.Chat chat=new TLRPC.Chat();
 public static MessagesController getInstance(int a){return new MessagesController();} public TLRPC.User getUser(long id){return sender;} public TLRPC.Chat getChat(long id){return chat;} }''',
'org/telegram/messenger/ChatObject.java': '''package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class ChatObject {
 public static boolean isNotInChat(TLRPC.Chat c){return false;} public static boolean isChannelAndNotMegaGroup(TLRPC.Chat c){return false;} public static boolean canSendPlain(TLRPC.Chat c){return true;} }''',
'org/telegram/messenger/DialogObject.java': 'package org.telegram.messenger; public class DialogObject { public static boolean isEncryptedDialog(long d){return d==999;} }',
'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger; import java.util.concurrent.*; public class AndroidUtilities {
 public static final BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>(); public static void runOnUIThread(Runnable r){ui.add(r);} }''',
'org/telegram/messenger/SendMessagesHelper.java': '''package org.telegram.messenger; public class SendMessagesHelper {
 public static int sent; public static long dialog; public static MessageObject replyTo;
 public static SendMessagesHelper getInstance(int a){return new SendMessagesHelper();} public void sendMessage(SendMessageParams p){sent++;dialog=p.did;replyTo=p.reply;}
 public static class SendMessageParams {long did;MessageObject reply; public static SendMessageParams of(String s,long d,MessageObject m,Object a,Object b,boolean c,Object e,Object f,Object g,boolean h,int i,int j,Object k,boolean l){SendMessageParams p=new SendMessageParams();p.did=d;p.reply=m;return p;} } }''',
'app/nimarkogram/messenger/ai/AutoReplyConfig.java': '''package app.nimarkogram.messenger.ai; import org.json.*; import java.util.*;
public class AutoReplyConfig { private static Map<Integer,AutoReplyConfig> all=new HashMap<>(); public static AutoReplyConfig get(int a){return all.computeIfAbsent(a,k->new AutoReplyConfig());}
 public Map<Long,JSONObject> chats=new HashMap<>(); public Map<String,Object> data=new HashMap<>(); public long revision;
 public JSONObject chat(long d){return chats.computeIfAbsent(d,k->new JSONObject());}
 public int getInt(String k,int f){return (Integer)data.getOrDefault(k,f);} public void putInt(String k,int v){data.put(k,v);}
 public String getString(String k,String f){return (String)data.getOrDefault(k,f);} public void putString(String k,String v){data.put(k,v);}
 public boolean getBoolean(String k,boolean f){return (Boolean)data.getOrDefault(k,f);} public String key(){return "secret";} public long connectionRevision(){return revision;}
}''',
'app/nimarkogram/messenger/ai/AutoReplyClient.java': '''package app.nimarkogram.messenger.ai; import org.json.*; import java.util.*; import java.util.concurrent.*;
public class AutoReplyClient {public static int calls;public static volatile boolean fail;public static JSONArray lastPrompt;public static final BlockingQueue<Boolean> started=new LinkedBlockingQueue<>();public static volatile CountDownLatch unblock=new CountDownLatch(0);
 public static List<String> ranked(String b,List<String> m){return m;}
 public static String generate(String b,String k,String m,JSONArray p,int max)throws ClientException {calls++;lastPrompt=p;started.add(true);try{if(!unblock.await(3,TimeUnit.SECONDS))throw new RuntimeException("blocked test");}catch(InterruptedException e){throw new RuntimeException(e);}if(fail)throw new ClientException();return "answer";}
 public static class ClientException extends Exception {public int status=500;}
}''',
'ControllerCheck.java': '''import app.nimarkogram.messenger.ai.*; import org.telegram.messenger.*; import org.telegram.tgnet.*;import org.json.*;import java.util.*;import java.util.concurrent.*;
public class ControllerCheck {
 static int checks; static AutoReplyController c=AutoReplyController.get(0);static AutoReplyConfig cfg=AutoReplyConfig.get(0);
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static MessageObject message(int id){MessageObject m=new MessageObject();m.messageOwner=new TLRPC.TL_message();m.messageOwner.id=id;m.messageOwner.date=ConnectionsManager.now;return m;}
 static void enable(long d)throws Exception {cfg.chat(d).put("enabled",true).put("owner",10).put("enabled_at",9990).put("generation",1);cfg.data.put("model","test");}
 static void live(long d,MessageObject... m){c.onLive(d,new ArrayList<>(Arrays.asList(m)));}
 static void finish()throws Exception {Runnable r=AndroidUtilities.ui.poll(4,TimeUnit.SECONDS);check(r!=null,"worker completion");r.run();}
 static void blocked()throws Exception {AutoReplyClient.unblock=new CountDownLatch(1);AutoReplyClient.started.clear();}
 static void started()throws Exception {check(AutoReplyClient.started.poll(4,TimeUnit.SECONDS)!=null,"generation started");}
 static void release()throws Exception {AutoReplyClient.unblock.countDown();finish();}
 public static void main(String[] args)throws Exception {try{run();System.out.println("Controller: "+checks+" assertions passed");System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}}
 static void run()throws Exception {
 live(100,message(1));check(AutoReplyClient.calls==0,"disabled is private");
 enable(101);MessageObject incoming=message(1);live(101,incoming);finish();check(SendMessagesHelper.sent==1&&SendMessagesHelper.dialog==101&&SendMessagesHelper.replyTo==incoming,"correct dialog/reply binding");
 int calls=AutoReplyClient.calls;live(101,incoming);check(AutoReplyClient.calls==calls,"deduplicate same update");
 live(101,message(2));check(AutoReplyClient.calls==calls,"per-chat minute rate limit");
 enable(102);blocked();live(102,message(1));started();cfg.chat(102).put("enabled",false);release();check(SendMessagesHelper.sent==1,"disable cancels in-flight send");
 enable(103);blocked();live(103,message(1));started();MessageObject own=message(2);own.out=true;live(103,own);release();check(SendMessagesHelper.sent==1,"manual send cancels pending response");
 enable(104);blocked();live(104,message(1));started();cfg.revision++;release();check(SendMessagesHelper.sent==1,"connection edit cancels pending response");
 enable(105);blocked();live(105,message(1));started();cfg.chat(105).put("generation",2);release();check(SendMessagesHelper.sent==1,"chat setting edit cancels pending response");
 enable(106);cfg.chat(106).put("owner",11);live(106,message(1));check(AutoReplyClient.calls==calls+4,"account reuse cannot inherit permission");
 long d=200;
 enable(d);MessageObject out=message(1);out.out=true;live(d++,out);
 enable(d);MessageObject self=message(1);self.sender=10;MessagesController.sender.id=10;live(d++,self);MessagesController.sender.id=20;
 enable(d);MessagesController.sender.bot=true;live(d++,message(1));MessagesController.sender.bot=false;
 enable(d);MessageObject via=message(1);via.messageOwner.via_bot_id=50;live(d++,via);
 enable(d);MessageObject service=message(1);service.messageOwner=new TLRPC.TL_messageService();service.messageOwner.id=1;service.messageOwner.date=10000;live(d++,service);
 enable(d);MessageObject action=message(1);action.messageOwner.action=new Object();live(d++,action);
 enable(d);MessageObject blank=message(1);blank.messageOwner.message="  ";live(d++,blank);
 enable(d);MessageObject old=message(1);old.messageOwner.date=9989;live(d++,old);
 enable(d);MessageObject future=message(1);future.messageOwner.date=10006;live(d++,future);
 enable(999);live(999,message(1));
 check(AutoReplyClient.calls==calls+4,"outgoing,self,bot,via-bot,service,action,blank,old,future,encrypted rejected");
 enable(300);blocked();live(300,message(1));started();enable(301);live(301,message(1));live(300,message(2));check(AutoReplyClient.calls==calls+5,"global and per-chat concurrency bounded");release();
 check(AutoReplyClient.lastPrompt.length()==2,"context default sends system and one new message only");
 enable(400);cfg.chat(400).put("context",1);
 ConnectionsManager.now+=61;live(400,message(1));finish();ConnectionsManager.now+=61;live(400,message(2));finish();check(AutoReplyClient.lastPrompt.length()==3,"requested context uses one prior message");
 ConnectionsManager.now+=61;live(400,message(3));finish();check(AutoReplyClient.lastPrompt.length()==3,"context remains bounded");
 ConnectionsManager.now+=61;calls=AutoReplyClient.calls;live(400,message(4));check(AutoReplyClient.calls==calls,"three requests per ten minutes");
 enable(500);blocked();live(500,message(1));started();UserConfig.user=11;release();UserConfig.user=10;check(SendMessagesHelper.sent==5,"account logout/relogin cancels queued response");
 enable(501);AutoReplyClient.fail=true;calls=AutoReplyClient.calls;live(501,message(1));finish();AutoReplyClient.fail=false;check(AutoReplyClient.calls==calls+1&&SendMessagesHelper.sent==5,"generation error does not send");
 enable(502);MessageObject first=message(1),latest=message(2);live(502,first,latest);finish();check(SendMessagesHelper.sent==6&&SendMessagesHelper.replyTo==latest,"newest message in batch wins and error released semaphore");
 enable(503);MessageObject stale=message(1);stale.messageOwner.date=ConnectionsManager.now-61;cfg.chat(503).put("enabled_at",0);calls=AutoReplyClient.calls;live(503,stale);check(AutoReplyClient.calls==calls,"history older than 60 seconds ignored");
 }
}'''
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--json-jar', type=Path)
    args = parser.parse_args()
    jars = list((Path.home()/'.gradle/caches/modules-2/files-2.1/org.json/json').glob('*/*/*.jar'))
    jar = args.json_jar or (jars[0] if jars else None)
    if not jar or not jar.is_file():
        parser.error('Pass --json-jar with an existing org.json runtime jar')
    if not shutil.which('javac') or not shutil.which('java'):
        parser.error('JDK must be on PATH')
    with tempfile.TemporaryDirectory(prefix='linkigram-ai-controller-') as directory:
        temp = Path(directory)
        for name, source in STUBS.items():
            target = temp/name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(source, encoding='utf-8')
        actual = ROOT/'TMessagesProj/src/main/java/app/nimarkogram/messenger/ai/AutoReplyController.java'
        sources = [str(p) for p in temp.rglob('*.java')] + [str(actual)]
        subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',str(temp/'classes'),*sources],check=True)
        subprocess.run(['java','-ea','-cp',str(temp/'classes')+os.pathsep+str(jar),'ControllerCheck'],check=True,timeout=30)

if __name__ == '__main__':
    main()
