import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[4]
JAVA = ROOT / 'TMessagesProj/src/main/java'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


class DeletedHistoryFixesTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which('javac') and shutil.which('java'), 'JDK required')
    def test_contact_author_never_uses_private_contact_name(self):
        source = (JAVA / 'app/nimarkogram/messenger/features/DeletedReplyQuote.java').read_text(encoding='utf-8')
        actual = method(source, 'private static String resolveAuthor(')
        harness = '''class Main {
 static class TLRPC { static class User {boolean contact; String name, username;} }
 static class DialogObject {static String getName(int a,long id){return "channel";}}
 static class MessagesController {static final MessagesController INSTANCE=new MessagesController(); TLRPC.User user;
  static MessagesController getInstance(int a){return INSTANCE;} TLRPC.User getUser(long id){return user;}}
 static class UserObject {static String getPublicUsername(TLRPC.User u){return u.username;} static String getUserName(TLRPC.User u){return u.name;}}
 ''' + actual + '''
 static void eq(String a,String b){if(!java.util.Objects.equals(a,b)) throw new AssertionError(a+" != "+b);}
 public static void main(String[] args){
  TLRPC.User u=new TLRPC.User(); u.contact=true; u.name="Private nickname"; u.username="public_user"; MessagesController.INSTANCE.user=u;
  eq("@public_user",resolveAuthor(0,1,null)); u.username=null; eq(null,resolveAuthor(0,1,null));
  u.contact=false; u.name="Public name"; eq("Public name",resolveAuthor(0,1,null));
  eq("channel",resolveAuthor(0,-1,null)); eq("Anonymous",resolveAuthor(0,0,"Anonymous"));
 }
}'''
        with tempfile.TemporaryDirectory() as folder:
            folder = pathlib.Path(folder)
            source_file = folder / 'Main.java'
            source_file.write_text(harness, encoding='utf-8')
            javac = pathlib.Path(shutil.which('javac')).resolve()
            java = javac.with_name('java.exe' if os.name == 'nt' else 'java')
            subprocess.run([str(javac), '-encoding', 'UTF-8', str(source_file)], check=True)
            subprocess.run([str(java), '-cp', str(folder), 'Main'], check=True)

    def test_reply_lookup_is_not_emitted_as_history(self):
        source = (JAVA / 'com/radolyn/ayugram/proprietary/AyuHistoryHook.java').read_text(encoding='utf-8')
        grouped = source[source.index('for (DeletedMessageFull groupedMessageFull'):source.index('ArrayList<Integer> uniqueReplyIds')]
        self.assertIn('messageId >= startId', grouped)
        self.assertIn('messageId <= endId', grouped)
        replies = source[source.index('for (DeletedMessageFull replyMessageFull'):source.index('ArrayList<TLRPC.User> fetchedUsers')]
        self.assertIn('replyMessagesById.put(', replies)
        self.assertNotIn('messagesById.put(', replies)
        self.assertIn('indexById.put(replyId, createMessageObject', source)

    def test_middle_page_does_not_default_to_beginning(self):
        source = (JAVA / 'org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
        section = source[source.index('final long fallbackStartId;'):source.index('android.util.Log.i("AyuDebug", "restore hook:')]
        self.assertIn('long fbEnd = minVal;', section)
        self.assertIn('ayuHookDeletedHistory(batchStartId, batchEndId, load_type)', section)


if __name__ == '__main__':
    unittest.main()
