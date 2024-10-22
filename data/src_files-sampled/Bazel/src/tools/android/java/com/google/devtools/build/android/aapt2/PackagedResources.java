package com.google.devtools.build.android.aapt2;

import com.google.auto.value.AutoValue;
import com.google.devtools.build.android.ResourcesZip;
import java.nio.file.Path;

@AutoValue
public abstract class PackagedResources {

  public abstract Path apk();

  public abstract Path proto();

  public abstract Path rTxt();

  public abstract Path proguardConfig();

  public abstract Path mainDexProguard();

  public abstract Path javaSourceDirectory();

  abstract Path resourceIds();

  public abstract Path attributes();

  public abstract Path packages();

  public static PackagedResources of(
      Path outPath,
      Path protoPath,
      Path rTxt,
      Path proguardConfig,
      Path mainDexProguard,
      Path javaSourceDirectory,
      Path resourceIds,
      Path attributes,
      Path packages) {
    return new AutoValue_PackagedResources(
        outPath,
        protoPath,
        rTxt,
        proguardConfig,
        mainDexProguard,
        javaSourceDirectory,
        resourceIds,
        attributes,
        packages);
  }

  public ResourcesZip asArchive() {
    return ResourcesZip.fromApkWithProto(proto(), attributes(), resourceIds(), packages());
  }
}
