import app.nimarkogram.messenger.ai.AutoReplyConfig;
import org.json.JSONObject;
import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
public class ConfigCheck {
 public static class TestStore extends KeyStoreSpi {
  public Key engineGetKey(String a,char[] p){return new SecretKeySpec(Arrays.copyOf(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),16),"AES");}
  public boolean engineContainsAlias(String a){return true;}
  public Certificate[] engineGetCertificateChain(String a){return null;} public Certificate engineGetCertificate(String a){return null;}public Date engineGetCreationDate(String a){return new Date();}
  public void engineSetKeyEntry(String a,Key k,char[] p,Certificate[] c){}public void engineSetKeyEntry(String a,byte[] k,Certificate[] c){}public void engineSetCertificateEntry(String a,Certificate c){}public void engineDeleteEntry(String a){}
  public Enumeration<String> engineAliases(){return Collections.emptyEnumeration();} public int engineSize(){return 0;}public boolean engineIsKeyEntry(String a){return true;}public boolean engineIsCertificateEntry(String a){return false;}public String engineGetCertificateAlias(Certificate c){return null;}
  public void engineStore(OutputStream o,char[] p){}public void engineLoad(InputStream i,char[] p){}
 }
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  Security.addProvider(new Provider("AndroidKeyStore",1.0,"TEST ONLY; not hardware keystore"){{put("KeyStore.AndroidKeyStore",TestStore.class.getName());}});
  File f=new File(System.getProperty("java.io.tmpdir"),"ai_autoreply_0"); f.delete();
  AutoReplyConfig config=AutoReplyConfig.get(0);
  check(!config.chat(99).optBoolean("enabled"));config.setKey("sensitive-test-key");
  byte[] before=java.nio.file.Files.readAllBytes(f.toPath());check(!new String(before,java.nio.charset.StandardCharsets.ISO_8859_1).contains("sensitive-test-key"));
  config.putString("name","provider");byte[] after=java.nio.file.Files.readAllBytes(f.toPath());check(!Arrays.equals(Arrays.copyOf(before,12),Arrays.copyOf(after,12)));
  config.saveChat(99,new JSONObject().put("enabled",true));check(config.chat(99).optLong("owner")==12345);check(config.chat(99).optLong("enabled_at")==1000000);check(config.chat(99).optLong("generation")==1);
  JSONObject detached=config.chat(99);detached.put("enabled",false);check(config.chat(99).optBoolean("enabled"));
  long revision=config.connectionRevision();config.putString("base","https://other.example/v1");check(!config.chat(99).optBoolean("enabled"));check(config.chat(99).optLong("generation")==2);check(config.connectionRevision()==revision+1);
  java.lang.reflect.Field singleton=AutoReplyConfig.class.getDeclaredField("INSTANCES");singleton.setAccessible(true);((Map)singleton.get(null)).clear();
  config=AutoReplyConfig.get(0);check(config.key().equals("sensitive-test-key"));check(config.getString("name","").equals("provider"));
  byte[] damaged=java.nio.file.Files.readAllBytes(f.toPath());damaged[damaged.length-1]^=1;java.nio.file.Files.write(f.toPath(),damaged);((Map)singleton.get(null)).clear();config=AutoReplyConfig.get(0);
  try{config.key();throw new AssertionError();}catch(Exception expected){}check(!config.chat(99).optBoolean("enabled"));try{config.putString("name","overwrite");throw new AssertionError();}catch(IllegalStateException expected){}
  check(Arrays.equals(java.nio.file.Files.readAllBytes(f.toPath()),damaged));f.delete();
  System.out.println("PASS: actual Config compile, AES-GCM encrypted persistence/reload, fresh IV, default off, detached chat copy, generation/owner/date, endpoint consent reset/revision, tamper fail-closed without overwrite. AndroidKeyStore and AtomicFile are substitutes; hardware behavior not tested.");
 }
}
