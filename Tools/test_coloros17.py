"""Compile the production ColorOS/You palettes against Android fakes; no Gradle.

Run with JDK 17 on PATH, or pass --java-home /path/to/jdk.
Pass --material-jar with classes.jar from Material Components 1.13.0.
Checks real HCT palettes, role contrast, switching, fallback and drawable insets.
"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--material-jar", required=True)
    args = parser.parse_args()
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(Path(args.java_home) / "bin" / ("javac" + suffix)) if args.java_home else shutil.which("javac")
    java = str(Path(args.java_home) / "bin" / ("java" + suffix)) if args.java_home else shutil.which("java")
    if not javac or not java:
        parser.error("JDK 17 is required")
    root = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java"
    production = [root / "app/nimarkogram/messenger/appearance" / name
                  for name in ("ColorOs17Theme.java", "LinkiGramYouTheme.java", "MaterialStyle.java", "ClientTheme.java")]
    text = "\n".join(p.read_text(encoding="utf-8") for p in production)
    keys = sorted(set(re.findall(r"Theme\.(key_\w+)", text)))
    real_theme = (root / "org/telegram/ui/ActionBar/Theme.java").read_text(encoding="utf-8")
    declared = set(re.findall(r"int (key_\w+)\s*=", real_theme))
    missing = set(keys) - declared
    if missing:
        raise AssertionError(f"Unknown Theme symbols: {sorted(missing)}")
    system_colors = sorted(set(re.findall(r"android.R.color.(\w+)", text)))
    stubs = {
        "android/graphics/PorterDuff.java": "package android.graphics; public class PorterDuff {public enum Mode {MULTIPLY}}",
        "android/graphics/PorterDuffColorFilter.java": "package android.graphics; public class PorterDuffColorFilter {public final int color;public PorterDuffColorFilter(int c,PorterDuff.Mode m){color=c;}}",
        "android/content/Context.java": "package android.content; public class Context { public static int seed=0xFF008577; public int getColor(int id){return seed;} }",
        "android/os/Build.java": "package android.os; public class Build {public static class VERSION {public static int SDK_INT=35;} public static class VERSION_CODES {public static final int S=31;} }",
        "android/R.java": "package android; public class R {public static class color {" + "".join(f"public static final int {k}={i+1};" for i,k in enumerate(system_colors)) + "}}",
        "android/graphics/Rect.java": "package android.graphics; public class Rect {public int left,top,right,bottom;}",
        "android/graphics/drawable/Drawable.java": "package android.graphics.drawable; import android.graphics.Rect; public class Drawable {public static int filterColor; public void setColorFilter(android.graphics.PorterDuffColorFilter f){filterColor=f.color;} public boolean getPadding(Rect p){p.left=p.top=p.right=p.bottom=4;return true;}}",
        "android/graphics/drawable/GradientDrawable.java": "package android.graphics.drawable; public class GradientDrawable extends Drawable {public static int baseColor,tintColor;public void setColor(int c){baseColor=c;} public void setTint(int c){tintColor=c;} public void setCornerRadius(float r){} public void setCornerRadii(float[] r){} public void setStroke(int w,int c){} }",
        "android/graphics/drawable/InsetDrawable.java": "package android.graphics.drawable; import android.graphics.Rect; public class InsetDrawable extends Drawable {int l,t,r,b;public InsetDrawable(Drawable d,int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;} public boolean getPadding(Rect p){p.left=l;p.top=t;p.right=r;p.bottom=b;return true;}}",
        "android/util/SparseIntArray.java": "package android.util; public class SparseIntArray {java.util.Map<Integer,Integer> m=new java.util.HashMap<>();public void put(int k,int v){m.put(k,v);}public int get(int k,int d){return m.getOrDefault(k,d);}}",
        "org/telegram/messenger/AndroidUtilities.java": "package org.telegram.messenger; public class AndroidUtilities {public static int dp(float n){return (int)n;}}",
        "org/telegram/messenger/ApplicationLoader.java": "package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}",
        "org/telegram/ui/ActionBar/Theme.java": "package org.telegram.ui.ActionBar; public class Theme {public static boolean dark;public static boolean isCurrentThemeDark(){return dark;}public static int multAlpha(int c,float a){return (c & 0xffffff) | (Math.round((c >>> 24)*a) << 24);}" + "".join(f"public static final int {k}={i+1};" for i,k in enumerate(keys)) + "}",
        "app/nimarkogram/messenger/NimarkoConfig.java": """package app.nimarkogram.messenger;
public class NimarkoConfig {
 public static boolean disableDividers=false;
 public static boolean m3ListItems(){return false;}
 static Pref p=new Pref(); public static Pref getPreferences(){return p;}
 public static class Pref {
  java.util.Map<String,Boolean> m=new java.util.HashMap<>();
  public boolean getBoolean(String k,boolean d){return m.getOrDefault(k,d);}
  public int getInt(String k,int d){return d;}
  public Pref edit(){return this;} public Pref putBoolean(String k,boolean v){m.put(k,v);return this;}
  public void apply(){}
 }
}""",
        "Check.java": """
import app.nimarkogram.messenger.appearance.*;
import app.nimarkogram.messenger.NimarkoConfig;
import org.telegram.ui.ActionBar.Theme;
public class Check {
 static int checks;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 static double luminance(int c){double[] a=new double[3]; for(int i=0;i<3;i++){double v=((c>>(16-i*8))&255)/255.0;a[i]=v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}return .2126*a[0]+.7152*a[1]+.0722*a[2];}
 static void contrast(int a,int b,double min,String label){double x=luminance(a),y=luminance(b);check((Math.max(x,y)+.05)/(Math.min(x,y)+.05)>=min,label);}
 public static void main(String[] args){
  int original=0xFFBA1234;
  check(!ColorOs17Theme.isEnabled(),"ColorOS must default off");
  check(ColorOs17Theme.resolveColor(Theme.key_dialogBackground,original)==original,"disabled passthrough");
  LinkiGramYouTheme.setEnabled(true);ColorOs17Theme.setEnabled(true);
  check(!LinkiGramYouTheme.isEnabled(),"ColorOS disables You");
  check(NimarkoConfig.getPreferences().getBoolean("colorOs17ThemeEnabled",false),"selection persists");
  ColorOs17Theme.setEnabled(false);
  check(LinkiGramYouTheme.isEnabled(),"previous You theme restored after disabling ColorOS");
  ColorOs17Theme.setEnabled(true);
  check(MaterialStyle.switchWidth()==44&&MaterialStyle.switchHeight()==26,"ColorOS switch fits its track");
  check(MaterialStyle.rowHeight(50)==60,"ColorOS settings row metrics");
  check(ColorOs17Theme.resolveColor(-500,original)==original,"special colors preserved");
  for(boolean dark:new boolean[]{false,true}){
   Theme.dark=dark;
   int surface=ColorOs17Theme.resolveColor(Theme.key_dialogBackground,original);
   int foreground=ColorOs17Theme.resolveColor(Theme.key_dialogTextBlack,original);
   contrast(foreground,surface,7,"dialog contrast");
   contrast(ColorOs17Theme.resolveColor(Theme.key_dialogTextGray,original),surface,4.5,"secondary text contrast");
   contrast(ColorOs17Theme.navigationBarColor(LinkiGramYouTheme.NAV_COLOR_SELECTED,original),ColorOs17Theme.navigationBarColor(LinkiGramYouTheme.NAV_COLOR_INDICATOR,original),7,"selected navigation contrast");
   contrast(ColorOs17Theme.resolveColor(Theme.key_chat_messageTextOut,original),ColorOs17Theme.resolveColor(Theme.key_chat_outBubble,original),7,"outgoing message contrast");
   check(LinkiGramYouTheme.resolveColor(Theme.key_player_background,original)==surface,"global overlay reaches player");
   android.os.Build.VERSION.SDK_INT=24;
   check(LinkiGramYouTheme.resolveColor(Theme.key_dialogBackground,original)==surface,"ColorOS works before Android 12");
   android.os.Build.VERSION.SDK_INT=35;
  }
  LinkiGramYouTheme.setEnabled(true);
  check(!ColorOs17Theme.isEnabled(),"You replaces ColorOS");
  check(MaterialStyle.switchWidth()==52&&MaterialStyle.switchHeight()==32,"Material switch geometry");
  for (boolean dark:new boolean[]{false,true}) {
   Theme.dark=dark;
   int popup=ClientTheme.resolveColor(Theme.key_actionBarDefaultSubmenuBackground,original);
   contrast(ClientTheme.resolveColor(Theme.key_actionBarDefaultSubmenuItem,original),popup,7,"Material menu contrast");
   contrast(ClientTheme.color(ClientTheme.ON_PRIMARY),ClientTheme.color(ClientTheme.PRIMARY),4.5,"Material filled button contrast");
   contrast(ClientTheme.color(ClientTheme.ON_CONTAINER),ClientTheme.color(ClientTheme.CONTAINER),7,"Material bubble contrast");
   contrast(ClientTheme.color(ClientTheme.ON_CONTAINER),ClientTheme.color(ClientTheme.CONTAINER_SELECTED),7,"selected bubble contrast");
   contrast(ClientTheme.color(ClientTheme.SECONDARY),ClientTheme.color(ClientTheme.SURFACE),4.5,"Material secondary text contrast");
   check(ClientTheme.resolveColor(Theme.key_chat_outBubbleSelected,original)!=ClientTheme.resolveColor(Theme.key_chat_outBubble,original),"selection remains visible");
   check(ClientTheme.resolveColor(Theme.key_chat_outBubbleGradient1,original)==0,"theme gradient cannot override tonal bubbles");
  }
  android.os.Build.VERSION.SDK_INT=24;
  check(ClientTheme.resolveColor(Theme.key_dialogBackground,original)!=original,"Material fallback before Android 12");
  android.os.Build.VERSION.SDK_INT=35;
  int before=ClientTheme.color(ClientTheme.PRIMARY);
  android.content.Context.seed=0xFFB24A20;
  check(before!=ClientTheme.color(ClientTheme.PRIMARY),"wallpaper seed invalidates palette");
  check(ClientTheme.resolveColor(-500,original)==original,"unmapped content colors preserved");
  ColorOs17Theme.setEnabled(true);
  android.graphics.drawable.Drawable base=new android.graphics.drawable.Drawable();
  android.graphics.Rect p=new android.graphics.Rect();
  ColorOs17Theme.surfaceDrawable(base,0xFF1C1C1E,true).getPadding(p);
  check(p.left==4&&p.top==4&&p.right==4&&p.bottom==4,"sheet content insets preserved");
  check(android.graphics.drawable.GradientDrawable.baseColor==0xFFFFFFFF&&android.graphics.drawable.Drawable.filterColor==0xFF1C1C1E,"surface retains a white base for caller tint filters");
  LinkiGramYouTheme.setEnabled(true);
  check(!ColorOs17Theme.isEnabled(),"You disables ColorOS");
  LinkiGramYouTheme.setEnabled(false);
  check(MaterialStyle.switchWidth()==37&&MaterialStyle.switchHeight()==20,"original switch metrics restored");
  check(LinkiGramYouTheme.resolveColor(Theme.key_dialogBackground,original)==original,"original theme restored");
  check(ColorOs17Theme.surfaceDrawable(base,0,false)==base,"original surface restored");
  System.out.println(checks+" client theme checks passed");
 }
}
""",
    }
    with tempfile.TemporaryDirectory(prefix="linkigram-coloros-") as tmp:
        folder = Path(tmp)
        for name, content in stubs.items():
            p = folder / name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content, encoding="utf-8")
        classes = folder / "classes"
        classes.mkdir()
        subprocess.run([javac, "-encoding", "UTF-8", "-cp", args.material_jar, "-d", str(classes),
                        *map(str, folder.rglob("*.java")), *map(str, production)], check=True)
        subprocess.run([java, "-cp", str(classes) + os.pathsep + args.material_jar, "Check"], check=True)


if __name__ == "__main__":
    main()
