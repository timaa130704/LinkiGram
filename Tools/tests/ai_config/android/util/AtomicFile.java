package android.util;
import java.io.*;
public class AtomicFile {
 private final File file; public AtomicFile(File f){file=f;} public File getBaseFile(){return file;}
 public byte[] readFully()throws IOException{return java.nio.file.Files.readAllBytes(file.toPath());}
 public FileOutputStream startWrite()throws IOException{return new FileOutputStream(new File(file+".new"));}
 public void finishWrite(FileOutputStream out)throws IOException{out.close();java.nio.file.Files.move(new File(file+".new").toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
 public void failWrite(FileOutputStream out){try{out.close();}catch(Exception e){}new File(file+".new").delete();}
}
