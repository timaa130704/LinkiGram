import app.nimarkogram.messenger.ai.AutoReplyClient;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
public class ClientCheck {
 static boolean rejectLegacyTokens=false; static final List<ByteArrayOutputStream> sent=new ArrayList<>();
 static int code=200; static String body; static String path; static HttpURLConnection current;
 public static void main(String[] args) throws Exception {
  URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() {
   protected URLConnection openConnection(URL u) { path=u.getPath(); return current=new HttpURLConnection(u) {
    public void disconnect() {} public boolean usingProxy(){ return false; } public void connect(){}
    public int getResponseCode(){ if(rejectLegacyTokens){rejectLegacyTokens=false;return 400;}return code; } public InputStream getInputStream(){ return new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public OutputStream getOutputStream(){ ByteArrayOutputStream out=new ByteArrayOutputStream();sent.add(out);return out; }
   }; }
  } : null);
  check(AutoReplyClient.normalizeBase("https://example.com/v1/").equals("https://example.com/v1"));
  check(AutoReplyClient.normalizeBase("https://example.com/v1/models").equals("https://example.com/v1"));
  check(AutoReplyClient.normalizeBase("https://example.com/v1/chat/completions").equals("https://example.com/v1"));
  for(String bad: Arrays.asList("http://example.com", "https://key@example.com", "https://example.com?key=secret", "https://example.com/#secret")) {
   try { AutoReplyClient.normalizeBase(bad); throw new AssertionError(); } catch(AutoReplyClient.ClientException expected){}
  }
  body="{\"data\":[{\"id\":\"slow\"},{\"id\":\"fast\"},{\"id\":\"fast\"}]}";
  check(AutoReplyClient.models("https://example.com/v1/models", "secret").equals(Arrays.asList("fast","slow")));
  check(path.equals("/v1/models")); check(!current.getInstanceFollowRedirects());
  body="{\"choices\":[{\"message\":{\"content\":\"Hello world\"}}]}";
  check(AutoReplyClient.generate("https://example.com/v1", "secret", "fast", new JSONArray(),5).equals("Hello"));
  check(path.equals("/v1/chat/completions"));
  sent.clear(); rejectLegacyTokens=true;
  check(AutoReplyClient.generate("https://example.com/v1","secret","modern",new JSONArray(),5).equals("Hello"));
  check(sent.size()==2);
  check(new JSONObject(sent.get(0).toString("UTF-8")).has("max_tokens"));
  JSONObject retried=new JSONObject(sent.get(1).toString("UTF-8"));check(!retried.has("max_tokens"));check(retried.has("max_completion_tokens"));
  body="{\"choices\":[]}";
  try { AutoReplyClient.generate("https://example.com/v1", "secret", "slow", new JSONArray(),5); throw new AssertionError(); } catch(AutoReplyClient.ClientException expected){}
  for(int status:new int[]{301,401,403,404,429,500}) { code=status; body="secret response";
   try { AutoReplyClient.models("https://example.com/v1","secret"); throw new AssertionError(); } catch(AutoReplyClient.ClientException expected) {check(!expected.getMessage().contains("secret")); check(expected.status==status);}
  }
  code=200; body="invalid secret JSON";
  try { AutoReplyClient.models("https://example.com/v1","secret"); throw new AssertionError(); } catch(AutoReplyClient.ClientException expected) {check(!expected.getMessage().contains("secret"));}
  java.lang.reflect.Method record=AutoReplyClient.class.getDeclaredMethod("record",String.class,String.class,long.class); record.setAccessible(true);
  AutoReplyClient.reset(); record.invoke(null,"https://example.com/v1","slow",900L);record.invoke(null,"https://example.com/v1","fast",100L);record.invoke(null,"https://example.com/v1","bad",-1L);
  check(AutoReplyClient.ranked("https://example.com/v1",Arrays.asList("bad","unknown","slow","fast")).equals(Arrays.asList("fast","slow","unknown","bad")));
  check(AutoReplyClient.ranked("https://other.com/v1",Arrays.asList("slow","fast")).equals(Arrays.asList("slow","fast")));
  AutoReplyClient.reset();
  body="{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";
  ArrayList<String> many=new ArrayList<>(); for(int i=0;i<15;i++)many.add("model"+i);
  check(AutoReplyClient.benchmark("https://example.com/v1","secret",many).size()==12);
  check(AutoReplyClient.benchmark("https://example.com/v1","secret",many).size()==15);
  java.lang.reflect.Method measurement=AutoReplyClient.class.getDeclaredMethod("measurement",String.class,String.class); measurement.setAccessible(true);
  record.invoke(null,"https://example.com/v1","fast",100L);
  AutoReplyClient.generate("https://example.com/v1","secret","fast",new JSONArray(),5);
  check(((Long)measurement.invoke(null,"https://example.com/v1","fast"))==100L);
  java.lang.reflect.Field field=AutoReplyClient.class.getDeclaredField("measurements");field.setAccessible(true);
  JSONObject stored=(JSONObject)field.get(null);
  for(String id:stored.keySet())stored.getJSONObject(id).put("at",0L);
  check(((Long)measurement.invoke(null,"https://example.com/v1","fast"))==Long.MAX_VALUE);
  AutoReplyClient.reset();
  System.out.println("PASS: URL normalization/rejection, models dedup, real request paths, redirects disabled, truncation, malformed response, status sanitization, ranking/cache endpoint isolation/reset");
 }
 static void check(boolean yes){ if(!yes)throw new AssertionError(); }
}
