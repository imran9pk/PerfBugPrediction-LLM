package com.google.devtools.build.android.desugar;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.invoke.MethodHandles.publicLookup;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.android.desugar.io.BitFlags;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

class LambdaDesugaring extends ClassVisitor {

  private final ClassLoader targetLoader;
  private final LambdaClassMaker lambdas;
  private final ImmutableSet.Builder<String> aggregateInterfaceLambdaMethods;
  private final Map<Handle, MethodReferenceBridgeInfo> bridgeMethods = new LinkedHashMap<>();
  private final ImmutableSet<MethodInfo> lambdaMethodsUsedInInvokeDyanmic;
  private final boolean allowDefaultMethods;

  private String internalName;
  private boolean isInterface;
  private int lambdaCount;

  public LambdaDesugaring(
      ClassVisitor dest,
      ClassLoader targetLoader,
      LambdaClassMaker lambdas,
      ImmutableSet.Builder<String> aggregateInterfaceLambdaMethods,
      ImmutableSet<MethodInfo> lambdaMethodsUsedInInvokeDyanmic,
      boolean allowDefaultMethods) {
    super(Opcodes.ASM8, dest);
    this.targetLoader = targetLoader;
    this.lambdas = lambdas;
    this.aggregateInterfaceLambdaMethods = aggregateInterfaceLambdaMethods;
    this.lambdaMethodsUsedInInvokeDyanmic = lambdaMethodsUsedInInvokeDyanmic;
    this.allowDefaultMethods = allowDefaultMethods;
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    checkState(internalName == null, "not intended for reuse but reused for %s", name);
    internalName = name;
    isInterface = BitFlags.isSet(access, Opcodes.ACC_INTERFACE);
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitEnd() {
    for (Map.Entry<Handle, MethodReferenceBridgeInfo> bridge : bridgeMethods.entrySet()) {
      Handle original = bridge.getKey();
      Handle neededMethod = bridge.getValue().bridgeMethod();
      checkState(
          neededMethod.getTag() == Opcodes.H_INVOKESTATIC
              || neededMethod.getTag() == Opcodes.H_INVOKEVIRTUAL,
          "Cannot generate bridge method %s to reach %s",
          neededMethod,
          original);
      checkState(
          bridge.getValue().referenced() != null,
          "Need referenced method %s to generate bridge %s",
          original,
          neededMethod);

      int access = Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_FINAL;
      if (neededMethod.getTag() == Opcodes.H_INVOKESTATIC) {
        access |= Opcodes.ACC_STATIC;
      }
      MethodVisitor bridgeMethod =
          super.visitMethod(
              access,
              neededMethod.getName(),
              neededMethod.getDesc(),
              (String) null,
              toInternalNames(bridge.getValue().referenced().getExceptionTypes()));

      if (original.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
        bridgeMethod.visitTypeInsn(Opcodes.NEW, original.getOwner());
        bridgeMethod.visitInsn(Opcodes.DUP);
      }

      int slot = 0;
      if (neededMethod.getTag() != Opcodes.H_INVOKESTATIC) {
        bridgeMethod.visitVarInsn(Opcodes.ALOAD, slot++);
      }
      Type neededType = Type.getMethodType(neededMethod.getDesc());
      for (Type arg : neededType.getArgumentTypes()) {
        bridgeMethod.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot);
        slot += arg.getSize();
      }
      bridgeMethod.visitMethodInsn(
          invokeOpcode(original),
          original.getOwner(),
          original.getName(),
          original.getDesc(),
          original.isInterface());
      bridgeMethod.visitInsn(neededType.getReturnType().getOpcode(Opcodes.IRETURN));

      bridgeMethod.visitMaxs(0, 0); bridgeMethod.visitEnd();
    }
    super.visitEnd();
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {
    if (name.equals("$deserializeLambda$") && BitFlags.isSet(access, Opcodes.ACC_SYNTHETIC)) {
      return null;
    }
    if (name.startsWith("lambda$")
        && BitFlags.isSet(access, Opcodes.ACC_SYNTHETIC)
        && lambdaMethodsUsedInInvokeDyanmic.contains(MethodInfo.create(internalName, name, desc))) {
      if (!allowDefaultMethods && isInterface && BitFlags.isSet(access, Opcodes.ACC_STATIC)) {
        aggregateInterfaceLambdaMethods.add(internalName + '#' + name);
        return null;
      }
      if (BitFlags.isSet(access, Opcodes.ACC_PRIVATE)) {
        access &= ~Opcodes.ACC_PRIVATE;
        if (allowDefaultMethods && isInterface) {
          access |= Opcodes.ACC_PUBLIC;
        } else {
          access |= Opcodes.ACC_FINAL;
        }
      }
      name = uniqueInPackage(internalName, name);
    }
    MethodVisitor dest = super.visitMethod(access, name, desc, signature, exceptions);
    return dest != null
        ? new InvokedynamicRewriter(dest, access, name, desc, signature, exceptions)
        : null;
  }

  @Override
  public void visitOuterClass(String owner, String name, String desc) {
    if (name != null && name.startsWith("lambda$")) {
      name = uniqueInPackage(owner, name);
    }
    super.visitOuterClass(owner, name, desc);
  }

  static String uniqueInPackage(String owner, String name) {
    String suffix = "$" + owner.substring(owner.lastIndexOf('/') + 1);
    return name.endsWith(suffix) ? name : name + suffix;
  }

  private MethodReferenceBridgeInfo queueUpBridgeMethodIfNeeded(Handle invokedMethod)
      throws ClassNotFoundException {
    if (invokedMethod.getName().startsWith("lambda$")) {
      return MethodReferenceBridgeInfo.noBridge(invokedMethod);
    }

    Executable invoked = findTargetMethod(invokedMethod);
    if (isVisibleToLambdaClass(invoked, invokedMethod.getOwner())) {
      return MethodReferenceBridgeInfo.noBridge(invokedMethod);
    }

    checkState(
        !isInterface,
        "%s is an interface and shouldn't need bridge to %s",
        internalName,
        invokedMethod);
    checkState(
        !invokedMethod.isInterface(),
        "%s's lambda classes can't see interface method: %s",
        internalName,
        invokedMethod);
    MethodReferenceBridgeInfo result = bridgeMethods.get(invokedMethod);
    if (result != null) {
      return result; }

    String name = uniqueInPackage(internalName, "bridge$lambda$" + bridgeMethods.size());
    Handle bridgeMethod;
    switch (invokedMethod.getTag()) {
      case Opcodes.H_INVOKESTATIC:
        bridgeMethod =
            new Handle(
                invokedMethod.getTag(), internalName, name, invokedMethod.getDesc(), false);
        break;
      case Opcodes.H_INVOKEVIRTUAL:
      case Opcodes.H_INVOKESPECIAL: bridgeMethod =
            new Handle(
                Opcodes.H_INVOKEVIRTUAL,
                internalName,
                name,
                invokedMethod.getDesc(), false);
        break;
      case Opcodes.H_NEWINVOKESPECIAL:
        {
          String desc =
              Type.getMethodDescriptor(
                  Type.getObjectType(invokedMethod.getOwner()),
                  Type.getArgumentTypes(invokedMethod.getDesc()));
          bridgeMethod =
              new Handle(Opcodes.H_INVOKESTATIC, internalName, name, desc, false);
          break;
        }
      case Opcodes.H_INVOKEINTERFACE:
        default:
        throw new UnsupportedOperationException("Cannot bridge " + invokedMethod);
    }
    result = MethodReferenceBridgeInfo.bridge(invokedMethod, invoked, bridgeMethod);
    MethodReferenceBridgeInfo old = bridgeMethods.put(invokedMethod, result);
    checkState(old == null, "Already had bridge %s so we don't also want %s", old, result);
    return result;
  }

  private boolean isVisibleToLambdaClass(Executable invoked, String owner) {
    int modifiers = invoked.getModifiers();
    if (Modifier.isPrivate(modifiers)) {
      return false;
    }
    if (Modifier.isPublic(modifiers)) {
      return true;
    }
    return packageName(internalName).equals(packageName(owner));
  }

  private Executable findTargetMethod(Handle invokedMethod) throws ClassNotFoundException {
    Type descriptor = Type.getMethodType(invokedMethod.getDesc());
    Class<?> owner = loadFromInternal(invokedMethod.getOwner());
    if (invokedMethod.getTag() == Opcodes.H_NEWINVOKESPECIAL) {
      for (Constructor<?> c : owner.getDeclaredConstructors()) {
        if (Type.getType(c).equals(descriptor)) {
          return c;
        }
      }
    } else {
      for (Method m : owner.getDeclaredMethods()) {
        if (m.getName().equals(invokedMethod.getName()) && Type.getType(m).equals(descriptor)) {
          return m;
        }
      }
    }
    throw new IllegalArgumentException("Referenced method not found: " + invokedMethod);
  }

  private Class<?> loadFromInternal(String internalName) throws ClassNotFoundException {
    return targetLoader.loadClass(internalName.replace('/', '.'));
  }

  static int invokeOpcode(Handle invokedMethod) {
    switch (invokedMethod.getTag()) {
      case Opcodes.H_INVOKESTATIC:
        return Opcodes.INVOKESTATIC;
      case Opcodes.H_INVOKEVIRTUAL:
        return Opcodes.INVOKEVIRTUAL;
      case Opcodes.H_INVOKESPECIAL:
      case Opcodes.H_NEWINVOKESPECIAL: return Opcodes.INVOKESPECIAL;
      case Opcodes.H_INVOKEINTERFACE:
        return Opcodes.INVOKEINTERFACE;
      default:
        throw new UnsupportedOperationException("Don't know how to call " + invokedMethod);
    }
  }

  private static String[] toInternalNames(Class<?>[] classes) {
    String[] result = new String[classes.length];
    for (int i = 0; i < classes.length; ++i) {
      result[i] = Type.getInternalName(classes[i]);
    }
    return result;
  }

  private static String packageName(String internalClassName) {
    int lastSlash = internalClassName.lastIndexOf('/');
    return lastSlash > 0 ? internalClassName.substring(0, lastSlash) : "";
  }

  private class InvokedynamicRewriter extends MethodNode {

    private final MethodVisitor dest;

    public InvokedynamicRewriter(
        MethodVisitor dest,
        int access,
        String name,
        String desc,
        String signature,
        String[] exceptions) {
      super(Opcodes.ASM8, access, name, desc, signature, exceptions);
      this.dest = checkNotNull(dest, "Null destination for %s.%s : %s", internalName, name, desc);
    }

    @Override
    public void visitEnd() {
      accept(dest);
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
      if (!"java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner())) {
        super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs);
        return;
      }

      try {
        Lookup lookup = createLookup(internalName);
        ArrayList<Object> args = new ArrayList<>(bsmArgs.length + 3);
        args.add(lookup);
        args.add(name);
        args.add(MethodType.fromMethodDescriptorString(desc, targetLoader));
        for (Object bsmArg : bsmArgs) {
          args.add(toJvmMetatype(lookup, bsmArg));
        }

        MethodReferenceBridgeInfo bridgeInfo = queueUpBridgeMethodIfNeeded((Handle) bsmArgs[1]);

        MethodHandle bsmMethod = toMethodHandle(publicLookup(), bsm, false);
        String lambdaClassName = internalName + "$$Lambda$" + (lambdaCount++);
        Type[] capturedTypes = Type.getArgumentTypes(desc);
        boolean needFactory =
            capturedTypes.length != 0
                && !attemptAllocationBeforeArgumentLoads(lambdaClassName, capturedTypes);
        lambdas.generateLambdaClass(
            internalName,
            LambdaInfo.create(
                lambdaClassName,
                desc,
                needFactory,
                bridgeInfo.methodReference(),
                bridgeInfo.bridgeMethod()),
            bsmMethod,
            args);
        if (desc.startsWith("()")) {
          checkState(capturedTypes.length == 0);
          super.visitFieldInsn(
              Opcodes.GETSTATIC,
              lambdaClassName,
              LambdaClassFixer.SINGLETON_FIELD_NAME,
              desc.substring("()".length()));
        } else if (needFactory) {
          super.visitMethodInsn(
              Opcodes.INVOKESTATIC,
              lambdaClassName,
              LambdaClassFixer.FACTORY_METHOD_NAME,
              desc,
              false);
        } else {
          super.visitMethodInsn(
              Opcodes.INVOKESPECIAL,
              lambdaClassName,
              "<init>",
              Type.getMethodDescriptor(Type.VOID_TYPE, capturedTypes),
              false);
        }
      } catch (IOException | ReflectiveOperationException e) {
        throw new IllegalStateException(
            "Couldn't desugar invokedynamic for "
                + internalName
                + "."
                + name
                + " using "
                + bsm
                + " with arguments "
                + Arrays.toString(bsmArgs),
            e);
      }
    }

    private boolean attemptAllocationBeforeArgumentLoads(String internalName, Type[] paramTypes) {
      checkArgument(paramTypes.length > 0, "Expected at least one param for %s", internalName);
      AbstractInsnNode insn = instructions.getLast();
      for (int i = paramTypes.length - 1; 0 <= i; --i) {
        if (insn.getOpcode() == Opcodes.GETFIELD) {
          FieldInsnNode getfield = (FieldInsnNode) insn;
          checkState(
              getfield.desc.length() == 1
                  ? getfield.desc.equals(paramTypes[i].getDescriptor())
                  : paramTypes[i].getDescriptor().length() > 1,
              "Expected getfield for %s to set up parameter %s for %s but got %s : %s",
              paramTypes[i],
              i,
              internalName,
              getfield.name,
              getfield.desc);
          insn = insn.getPrevious();

          while (insn.getOpcode() == Opcodes.GETFIELD) {
            checkState(
                ((FieldInsnNode) insn).desc.startsWith("L"),
                "expect object type getfields to get to %s to set up parameter %s for %s, not: %s",
                paramTypes[i],
                i,
                internalName,
                ((FieldInsnNode) insn).desc);
            insn = insn.getPrevious();
          }

          checkState(
              insn.getOpcode() == Opcodes.ALOAD, "Expected aload before getfield for %s to set up parameter %s for %s but got %s",
              getfield.name,
              i,
              internalName,
              insn.getOpcode());
        } else if (!isPushForType(insn, paramTypes[i])) {
          checkState(
              paramTypes.length == 1,
              "Expected a load for %s to set up parameter %s for %s but got %s",
              paramTypes[i],
              i,
              internalName,
              insn.getOpcode());
          return false;
        }
        insn = insn.getPrevious();
      }

      TypeInsnNode newInsn = new TypeInsnNode(Opcodes.NEW, internalName);
      if (insn == null) {
        instructions.insert(newInsn);
      } else {
        instructions.insert(insn, newInsn);
      }
      instructions.insert(newInsn, new InsnNode(Opcodes.DUP));
      return true;
    }

    private boolean isPushForType(AbstractInsnNode insn, Type type) {
      int opcode = insn.getOpcode();
      if (opcode == type.getOpcode(Opcodes.ILOAD)) {
        return true;
      }
      switch (type.getSort()) {
        case Type.BOOLEAN:
          return opcode == Opcodes.ICONST_0 || opcode == Opcodes.ICONST_1;

        case Type.BYTE:
        case Type.CHAR:
        case Type.SHORT:
        case Type.INT:
          return opcode == Opcodes.SIPUSH
              || opcode == Opcodes.ICONST_0
              || opcode == Opcodes.ICONST_1
              || opcode == Opcodes.ICONST_2
              || opcode == Opcodes.ICONST_3
              || opcode == Opcodes.ICONST_4
              || opcode == Opcodes.ICONST_5
              || opcode == Opcodes.ICONST_M1;

        case Type.LONG:
          return opcode == Opcodes.LCONST_0 || opcode == Opcodes.LCONST_1;

        case Type.FLOAT:
          return opcode == Opcodes.FCONST_0
              || opcode == Opcodes.FCONST_1
              || opcode == Opcodes.FCONST_2;

        case Type.DOUBLE:
          return opcode == Opcodes.DCONST_0 || opcode == Opcodes.DCONST_1;

        case Type.OBJECT:
        case Type.ARRAY:
          return opcode == Opcodes.ACONST_NULL;

        default:
          return false;
      }
    }

    private Lookup createLookup(String lookupClass) throws ReflectiveOperationException {
      Class<?> clazz = loadFromInternal(lookupClass);
      Constructor<Lookup> constructor = Lookup.class.getDeclaredConstructor(Class.class);
      constructor.setAccessible(true);
      return constructor.newInstance(clazz);
    }

    private Object toJvmMetatype(Lookup lookup, Object asm) throws ReflectiveOperationException {
      if (asm instanceof Number) {
        return asm;
      }
      if (asm instanceof Type) {
        Type type = (Type) asm;
        switch (type.getSort()) {
          case Type.OBJECT:
            return loadFromInternal(type.getInternalName());
          case Type.METHOD:
            return MethodType.fromMethodDescriptorString(type.getDescriptor(), targetLoader);
          default:
            throw new IllegalArgumentException("Cannot convert: " + asm);
        }
      }
      if (asm instanceof Handle) {
        return toMethodHandle(lookup, (Handle) asm, true);
      }
      throw new IllegalArgumentException("Cannot convert: " + asm);
    }

    private MethodHandle toMethodHandle(Lookup lookup, Handle asmHandle, boolean target)
        throws ReflectiveOperationException {
      Class<?> owner = loadFromInternal(asmHandle.getOwner());
      MethodType signature =
          MethodType.fromMethodDescriptorString(
              asmHandle.getDesc(),
              target ? targetLoader : Thread.currentThread().getContextClassLoader());
      switch (asmHandle.getTag()) {
        case Opcodes.H_INVOKESTATIC:
          return lookup.findStatic(owner, asmHandle.getName(), signature);
        case Opcodes.H_INVOKEVIRTUAL:
        case Opcodes.H_INVOKEINTERFACE:
          return lookup.findVirtual(owner, asmHandle.getName(), signature);
        case Opcodes.H_INVOKESPECIAL: return lookup.findSpecial(owner, asmHandle.getName(), signature, owner);
        case Opcodes.H_NEWINVOKESPECIAL:
          return lookup.findConstructor(owner, signature);
        default:
          throw new UnsupportedOperationException("Cannot resolve " + asmHandle);
      }
    }
  }

  @AutoValue
  abstract static class MethodReferenceBridgeInfo {
    public static MethodReferenceBridgeInfo noBridge(Handle methodReference) {
      return new AutoValue_LambdaDesugaring_MethodReferenceBridgeInfo(
          methodReference, (Executable) null, methodReference);
    }

    public static MethodReferenceBridgeInfo bridge(
        Handle methodReference, Executable referenced, Handle bridgeMethod) {
      checkArgument(!bridgeMethod.equals(methodReference));
      return new AutoValue_LambdaDesugaring_MethodReferenceBridgeInfo(
          methodReference, checkNotNull(referenced), bridgeMethod);
    }

    public abstract Handle methodReference();

    @Nullable
    public abstract Executable referenced();

    public abstract Handle bridgeMethod();
  }
}
