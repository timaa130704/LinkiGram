package org.telegram.messenger;
public class ApplicationLoader { public static Context applicationContext = new Context(); public static class Context { public java.io.File getNoBackupFilesDir() { return new java.io.File(System.getProperty("java.io.tmpdir")); } } }
