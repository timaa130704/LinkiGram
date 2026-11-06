#!/usr/bin/env python3
"""Exercise the production lrcmux response parser against offline fixtures."""
import os
import argparse
from pathlib import Path
import subprocess
import tempfile
import shutil

root = Path(__file__).resolve().parents[1]
source = (root / 'TMessagesProj/src/main/java/app/exteraless/player/OnlineLyrics.java').read_text(encoding='utf-8')
start = source.index('    private static Found requestLrcmux(')
end = source.index('    private static Found requestNetease(', start)
method = source[start:end]
parser = argparse.ArgumentParser()
parser.add_argument('--json-jar', type=Path, required=True)
jar = parser.parse_args().json_jar.resolve()
javac = shutil.which('javac')
java = os.environ.get('JAVA') or (str(Path(javac).with_name('java.exe' if os.name == 'nt' else 'java')) if javac else 'java')
code = r'''import java.util.*; import org.json.*;
class LrcmuxCheck {
 static final String LRCMUX="https://example.test", LRCMUX_NAME="lrcmux";
 static final int MAX_FALLBACK_DURATION_DIFF=20; static String body, requested;
 static class TextUtils {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class Query {String title="song",artist="artist",album=null;int duration=180;}
 static class Found {Lyrics lyrics;Found(Lyrics l){lyrics=l;}}
 static class Lyrics {
  static final int SOURCE_ONLINE=1; ArrayList<Line> lines; String provider; boolean instrumental;
  static class Word {long time;int start,end;Word(long t,int s,int e){time=t;start=s;end=e;}}
  static class Line {long time;String text;ArrayList<Word> words;Line(long t,String s,ArrayList<Word> w){time=t;text=s;words=w;}}
  static Lyrics synced(ArrayList<Line> x,int s,String p){Lyrics l=new Lyrics();l.lines=x;l.provider=p;return l;}
  static Lyrics parse(String x,int s,String p){Lyrics l=new Lyrics();l.provider=p;l.lines=new ArrayList<>();return l;}
  static Lyrics instrumental(int s,String p){Lyrics l=new Lyrics();l.instrumental=true;l.provider=p;return l;}
 }
 static String enc(String s)throws Exception{return java.net.URLEncoder.encode(s,"UTF-8");}
 static String get(String u){requested=u;return body;}
 static String optString(JSONObject o,String key){return o.isNull(key)?null:o.optString(key,null);}
''' + method + r'''
 public static void main(String[] args)throws Exception{
  Query q=new Query();
  body="{\"track\":{\"duration\":180},\"meta\":{\"level\":\"word\",\"source\":{\"name\":\"Deezer\"}},\"lines\":[{\"start\":1200,\"text\":\"Hello world\",\"words\":[{\"text\":\"Hello\",\"start\":1200},{\"text\":\"world\",\"start\":1700}]}]}";
  Lyrics l=requestLrcmux(q,"deezer").lyrics;
  if(!requested.contains("level=word")||!requested.contains("sources=deezer"))throw new AssertionError("query");
  if(l.lines.size()!=1||l.lines.get(0).words.size()!=2||l.lines.get(0).words.get(1).time!=1700||l.lines.get(0).words.get(1).start!=6)throw new AssertionError("word timing");
  body=body.replace("world", "missing").replace("Hello missing", "Hello world");
  if(!requestLrcmux(q,"deezer").lyrics.lines.get(0).words.isEmpty())throw new AssertionError("invalid words");
  System.out.println("lrcmux word fixtures passed");
 }
}'''
with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / 'LrcmuxCheck.java'
    path.write_text(code, encoding='utf-8')
    subprocess.run([java, '-cp', str(jar), str(path)], check=True)
