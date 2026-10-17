package android.security.keystore;
public class KeyGenParameterSpec implements java.security.spec.AlgorithmParameterSpec {
 public static class Builder { public Builder(String alias,int purpose){} public Builder setBlockModes(String... modes){return this;} public Builder setEncryptionPaddings(String... modes){return this;} public KeyGenParameterSpec build(){return new KeyGenParameterSpec();} }
}
