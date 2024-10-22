package org.elasticsearch.painless;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.painless.Compiler.Loader;
import org.elasticsearch.painless.lookup.PainlessLookupBuilder;
import org.elasticsearch.painless.spi.Whitelist;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScriptException;
import org.elasticsearch.script.SearchScript;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.elasticsearch.painless.WriterConstants.OBJECT_TYPE;
import static org.elasticsearch.painless.node.SSource.MainMethodReserved;

public final class PainlessScriptEngine implements ScriptEngine {

    public static final String NAME = "painless";

    private static final AccessControlContext COMPILATION_CONTEXT;

    static {
        final Permissions none = new Permissions();
        none.setReadOnly();
        COMPILATION_CONTEXT = new AccessControlContext(new ProtectionDomain[] {
            new ProtectionDomain(null, none)
        });
    }

    private final CompilerSettings defaultCompilerSettings = new CompilerSettings();

    private final Map<ScriptContext<?>, Compiler> contextsToCompilers;

    public PainlessScriptEngine(Settings settings, Map<ScriptContext<?>, List<Whitelist>> contexts) {
        defaultCompilerSettings.setRegexesEnabled(CompilerSettings.REGEX_ENABLED.get(settings));

        Map<ScriptContext<?>, Compiler> contextsToCompilers = new HashMap<>();

        for (Map.Entry<ScriptContext<?>, List<Whitelist>> entry : contexts.entrySet()) {
            ScriptContext<?> context = entry.getKey();
            if (context.instanceClazz.equals(SearchScript.class)) {
                contextsToCompilers.put(context, new Compiler(GenericElasticsearchScript.class, null, null,
                        PainlessLookupBuilder.buildFromWhitelists(entry.getValue())));
            } else {
                contextsToCompilers.put(context, new Compiler(context.instanceClazz, context.factoryClazz, context.statefulFactoryClazz,
                        PainlessLookupBuilder.buildFromWhitelists(entry.getValue())));
            }
        }

        this.contextsToCompilers = Collections.unmodifiableMap(contextsToCompilers);
    }

    @Override
    public String getType() {
        return NAME;
    }

    @Override
    public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
        Compiler compiler = contextsToCompilers.get(context);

        if (context.instanceClazz.equals(SearchScript.class)) {
            Constructor<?> constructor = compile(compiler, scriptName, scriptSource, params);
            boolean needsScore;

            try {
                GenericElasticsearchScript newInstance = (GenericElasticsearchScript)constructor.newInstance();
                needsScore = newInstance.needs_score();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalArgumentException("internal error");
            }

            SearchScript.Factory factory = (p, lookup) -> new SearchScript.LeafFactory() {
                @Override
                public SearchScript newInstance(final LeafReaderContext context) {
                    try {
                        GenericElasticsearchScript newInstance = (GenericElasticsearchScript)constructor.newInstance();
                        return new ScriptImpl(newInstance, p, lookup, context);
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        throw new IllegalArgumentException("internal error");
                    }
                }
                @Override
                public boolean needs_score() {
                    return needsScore;
                }
            };
            return context.factoryClazz.cast(factory);
        } else {
            SpecialPermission.check();

            final Loader loader = AccessController.doPrivileged(new PrivilegedAction<Loader>() {
                @Override
                public Loader run() {
                    return compiler.createLoader(getClass().getClassLoader());
                }
            });

            MainMethodReserved reserved = new MainMethodReserved();
            compile(contextsToCompilers.get(context), loader, reserved, scriptName, scriptSource, params);

            if (context.statefulFactoryClazz != null) {
                return generateFactory(loader, context, reserved, generateStatefulFactory(loader, context, reserved));
            } else {
                return generateFactory(loader, context, reserved, WriterConstants.CLASS_TYPE);
            }
        }
    }

    private <T> Type generateStatefulFactory(Loader loader, ScriptContext<T> context, MainMethodReserved reserved) {
        int classFrames = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
        int classAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL;
        String interfaceBase = Type.getType(context.statefulFactoryClazz).getInternalName();
        String className = interfaceBase + "$StatefulFactory";
        String classInterfaces[] = new String[] { interfaceBase };

        ClassWriter writer = new ClassWriter(classFrames);
        writer.visit(WriterConstants.CLASS_VERSION, classAccess, className, null, OBJECT_TYPE.getInternalName(), classInterfaces);

        Method newFactory = null;

        for (Method method : context.factoryClazz.getMethods()) {
            if ("newFactory".equals(method.getName())) {
                newFactory = method;

                break;
            }
        }

        for (int count = 0; count < newFactory.getParameterTypes().length; ++count) {
            writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "$arg" + count,
                Type.getType(newFactory.getParameterTypes()[count]).getDescriptor(), null, null).visitEnd();
        }

        org.objectweb.asm.commons.Method base =
            new org.objectweb.asm.commons.Method("<init>", MethodType.methodType(void.class).toMethodDescriptorString());
        org.objectweb.asm.commons.Method init = new org.objectweb.asm.commons.Method("<init>",
            MethodType.methodType(void.class, newFactory.getParameterTypes()).toMethodDescriptorString());

        GeneratorAdapter constructor = new GeneratorAdapter(Opcodes.ASM5, init,
            writer.visitMethod(Opcodes.ACC_PUBLIC, init.getName(), init.getDescriptor(), null, null));
        constructor.visitCode();
        constructor.loadThis();
        constructor.invokeConstructor(OBJECT_TYPE, base);

        for (int count = 0; count < newFactory.getParameterTypes().length; ++count) {
            constructor.loadThis();
            constructor.loadArg(count);
            constructor.putField(Type.getType(className), "$arg" + count, Type.getType(newFactory.getParameterTypes()[count]));
        }

        constructor.returnValue();
        constructor.endMethod();

        Method newInstance = null;

        for (Method method : context.statefulFactoryClazz.getMethods()) {
            if ("newInstance".equals(method.getName())) {
                newInstance = method;

                break;
            }
        }

        org.objectweb.asm.commons.Method instance = new org.objectweb.asm.commons.Method(newInstance.getName(),
            MethodType.methodType(newInstance.getReturnType(), newInstance.getParameterTypes()).toMethodDescriptorString());

        List<Class<?>> parameters = new ArrayList<>(Arrays.asList(newFactory.getParameterTypes()));
        parameters.addAll(Arrays.asList(newInstance.getParameterTypes()));

        org.objectweb.asm.commons.Method constru = new org.objectweb.asm.commons.Method("<init>",
            MethodType.methodType(void.class, parameters.toArray(new Class<?>[] {})).toMethodDescriptorString());

        GeneratorAdapter adapter = new GeneratorAdapter(Opcodes.ASM5, instance,
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                instance.getName(), instance.getDescriptor(), null, null));
        adapter.visitCode();
        adapter.newInstance(WriterConstants.CLASS_TYPE);
        adapter.dup();

        for (int count = 0; count < newFactory.getParameterTypes().length; ++count) {
            adapter.loadThis();
            adapter.getField(Type.getType(className), "$arg" + count, Type.getType(newFactory.getParameterTypes()[count]));
        }

        adapter.loadArgs();
        adapter.invokeConstructor(WriterConstants.CLASS_TYPE, constru);
        adapter.returnValue();
        adapter.endMethod();

        writeNeedsMethods(context.statefulFactoryClazz, writer, reserved);
        writer.visitEnd();

        loader.defineFactory(className.replace('/', '.'), writer.toByteArray());

        return Type.getType(className);
    }

    private <T> T generateFactory(Loader loader, ScriptContext<T> context, MainMethodReserved reserved, Type classType) {
        int classFrames = ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS;
        int classAccess = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER| Opcodes.ACC_FINAL;
        String interfaceBase = Type.getType(context.factoryClazz).getInternalName();
        String className = interfaceBase + "$Factory";
        String classInterfaces[] = new String[] { interfaceBase };

        ClassWriter writer = new ClassWriter(classFrames);
        writer.visit(WriterConstants.CLASS_VERSION, classAccess, className, null, OBJECT_TYPE.getInternalName(), classInterfaces);

        org.objectweb.asm.commons.Method init =
            new org.objectweb.asm.commons.Method("<init>", MethodType.methodType(void.class).toMethodDescriptorString());

        GeneratorAdapter constructor = new GeneratorAdapter(Opcodes.ASM5, init,
                writer.visitMethod(Opcodes.ACC_PUBLIC, init.getName(), init.getDescriptor(), null, null));
        constructor.visitCode();
        constructor.loadThis();
        constructor.invokeConstructor(OBJECT_TYPE, init);
        constructor.returnValue();
        constructor.endMethod();

        Method reflect = null;

        for (Method method : context.factoryClazz.getMethods()) {
            if ("newInstance".equals(method.getName())) {
                reflect = method;

                break;
            } else if ("newFactory".equals(method.getName())) {
                reflect = method;

                break;
            }
        }

        org.objectweb.asm.commons.Method instance = new org.objectweb.asm.commons.Method(reflect.getName(),
            MethodType.methodType(reflect.getReturnType(), reflect.getParameterTypes()).toMethodDescriptorString());
        org.objectweb.asm.commons.Method constru = new org.objectweb.asm.commons.Method("<init>",
            MethodType.methodType(void.class, reflect.getParameterTypes()).toMethodDescriptorString());

        GeneratorAdapter adapter = new GeneratorAdapter(Opcodes.ASM5, instance,
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                                   instance.getName(), instance.getDescriptor(), null, null));
        adapter.visitCode();
        adapter.newInstance(classType);
        adapter.dup();
        adapter.loadArgs();
        adapter.invokeConstructor(classType, constru);
        adapter.returnValue();
        adapter.endMethod();

        writeNeedsMethods(context.factoryClazz, writer, reserved);
        writer.visitEnd();

        Class<?> factory = loader.defineFactory(className.replace('/', '.'), writer.toByteArray());

        try {
            return context.factoryClazz.cast(factory.getConstructor().newInstance());
        } catch (Exception exception) { throw new IllegalStateException(
                "An internal error occurred attempting to define the factory class [" + className + "].", exception);
        }
    }

    private void writeNeedsMethods(Class<?> clazz, ClassWriter writer, MainMethodReserved reserved) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().startsWith("needs") &&
                method.getReturnType().equals(boolean.class) && method.getParameterTypes().length == 0) {
                String name = method.getName();
                name = name.substring(5);
                name = Character.toLowerCase(name.charAt(0)) + name.substring(1);

                org.objectweb.asm.commons.Method needs = new org.objectweb.asm.commons.Method(method.getName(),
                    MethodType.methodType(boolean.class).toMethodDescriptorString());

                GeneratorAdapter adapter = new GeneratorAdapter(Opcodes.ASM5, needs,
                    writer.visitMethod(Opcodes.ACC_PUBLIC, needs.getName(), needs.getDescriptor(), null, null));
                adapter.visitCode();
                adapter.push(reserved.getUsedVariables().contains(name));
                adapter.returnValue();
                adapter.endMethod();
            }
        }
    }

    Constructor<?> compile(Compiler compiler, String scriptName, String source, Map<String, String> params) {
        final CompilerSettings compilerSettings = buildCompilerSettings(params);

        SpecialPermission.check();

        final Loader loader = AccessController.doPrivileged(new PrivilegedAction<Loader>() {
            @Override
            public Loader run() {
                return compiler.createLoader(getClass().getClassLoader());
            }
        });

        try {
            return AccessController.doPrivileged(new PrivilegedAction<Constructor<?>>() {
                @Override
                public Constructor<?> run() {
                    String name = scriptName == null ? source : scriptName;
                    Constructor<?> constructor = compiler.compile(loader, new MainMethodReserved(), name, source, compilerSettings);

                    try {
                        return constructor;
                    } catch (Exception exception) { throw new IllegalStateException(
                            "An internal error occurred attempting to define the script [" + name + "].", exception);
                    }
                }
            }, COMPILATION_CONTEXT);
        } catch (OutOfMemoryError | StackOverflowError | VerifyError | Exception e) {
            throw convertToScriptException(source, e);
        }
    }

    void compile(Compiler compiler, Loader loader, MainMethodReserved reserved,
                 String scriptName, String source, Map<String, String> params) {
        final CompilerSettings compilerSettings = buildCompilerSettings(params);

        try {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    String name = scriptName == null ? source : scriptName;
                    compiler.compile(loader, reserved, name, source, compilerSettings);

                    return null;
                }
            }, COMPILATION_CONTEXT);
            } catch (OutOfMemoryError | StackOverflowError | VerifyError | Exception e) {
            throw convertToScriptException(source, e);
        }
    }

    private CompilerSettings buildCompilerSettings(Map<String, String> params) {
        CompilerSettings compilerSettings;
        if (params.isEmpty()) {
            compilerSettings = defaultCompilerSettings;
        } else {
            compilerSettings = new CompilerSettings();

            compilerSettings.setRegexesEnabled(defaultCompilerSettings.areRegexesEnabled());

            Map<String, String> copy = new HashMap<>(params);

            String value = copy.remove(CompilerSettings.MAX_LOOP_COUNTER);
            if (value != null) {
                compilerSettings.setMaxLoopCounter(Integer.parseInt(value));
            }

            value = copy.remove(CompilerSettings.PICKY);
            if (value != null) {
                compilerSettings.setPicky(Boolean.parseBoolean(value));
            }

            value = copy.remove(CompilerSettings.INITIAL_CALL_SITE_DEPTH);
            if (value != null) {
                compilerSettings.setInitialCallSiteDepth(Integer.parseInt(value));
            }

            value = copy.remove(CompilerSettings.REGEX_ENABLED.getKey());
            if (value != null) {
                throw new IllegalArgumentException("[painless.regex.enabled] can only be set on node startup.");
            }

            if (!copy.isEmpty()) {
                throw new IllegalArgumentException("Unrecognized compile-time parameter(s): " + copy);
            }
        }
        return compilerSettings;
    }

    private ScriptException convertToScriptException(String scriptSource, Throwable t) {
        List<String> scriptStack = new ArrayList<>();
        for (StackTraceElement element : t.getStackTrace()) {
            if (WriterConstants.CLASS_NAME.equals(element.getClassName())) {
                int offset = element.getLineNumber();
                if (offset == -1) {
                    scriptStack.add("<<< unknown portion of script >>>");
                } else {
                    offset--; int startOffset = getPreviousStatement(offset);
                    int endOffset = getNextStatement(scriptSource, offset);
                    StringBuilder snippet = new StringBuilder();
                    if (startOffset > 0) {
                        snippet.append("... ");
                    }
                    snippet.append(scriptSource.substring(startOffset, endOffset));
                    if (endOffset < scriptSource.length()) {
                        snippet.append(" ...");
                    }
                    scriptStack.add(snippet.toString());
                    StringBuilder pointer = new StringBuilder();
                    if (startOffset > 0) {
                        pointer.append("    ");
                    }
                    for (int i = startOffset; i < offset; i++) {
                        pointer.append(' ');
                    }
                    pointer.append("^---- HERE");
                    scriptStack.add(pointer.toString());
                }
                break;
            }
        }
        throw new ScriptException("compile error", t, scriptStack, scriptSource, PainlessScriptEngine.NAME);
    }

    private int getPreviousStatement(int offset) {
        return Math.max(0, offset - 25);
    }

    private int getNextStatement(String scriptSource, int offset) {
        return Math.min(scriptSource.length(), offset + 25);
    }
}
