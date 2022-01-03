package neko.caffeine.sts.fix.core;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import amadeus.maho.lang.SneakyThrows;

import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import neko.caffeine.sts.fix.agent.AgentInjector;
import neko.caffeine.sts.fix.agent.LiveInjector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.objectweb.asm.Opcodes.*;

@SneakyThrows
@SpireInitializer
public class Initializer {
    
    public static class TextProvider {
        
        public Object text;
        
        public MethodInsnNode handler;
        
        public TextProvider(final Object text, final MethodInsnNode handler) {
            this.text = text;
            this.handler = handler;
        }
        
    }
    
    public static final String
            TYPE_HOOK_METHODS              = "neko/caffeine/sts/fix/core/HookMethods",
            TYPE_SETTINGS                  = "com/megacrit/cardcrawl/core/Settings",
            FIELD_LINE_BREAK_VIA_CHARACTER = "lineBreakViaCharacter";
    
    public static final MethodInsnNode
            HAS_CN_STRING = new MethodInsnNode(INVOKESTATIC, TYPE_HOOK_METHODS, "hasZN", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(String.class)), false),
            HAS_CN_LIST   = new MethodInsnNode(INVOKESTATIC, TYPE_HOOK_METHODS, "hasZN", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(List.class)), false),
            HAS_CN_OBJECT = new MethodInsnNode(INVOKESTATIC, TYPE_HOOK_METHODS, "hasZN", Type.getMethodDescriptor(Type.BOOLEAN_TYPE, Type.getType(Object.class)), false);
    
    public static final Map<String, TextProvider> TEXT_PROViDER_MAPPING = new HashMap<>();
    
    static {
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/cards/AbstractCard", new TextProvider("rawDescription", HAS_CN_STRING));
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/cutscenes/NeowNarrationScreen", new TextProvider("words", HAS_CN_LIST));
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/events/GenericEventDialog", new TextProvider("words", HAS_CN_LIST));
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/events/RoomEventDialog", new TextProvider("words", HAS_CN_LIST));
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/vfx/MegaDialogTextEffect", new TextProvider("words", HAS_CN_LIST));
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/vfx/SpeechTextEffect", new TextProvider("words", HAS_CN_LIST));
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/screens/SingleCardViewPopup", new TextProvider("card", HAS_CN_OBJECT));
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/helpers/FontHelper", null);
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/helpers/FontHelper#renderSmartText", new TextProvider(2, HAS_CN_STRING));
        TEXT_PROViDER_MAPPING.put("com/megacrit/cardcrawl/helpers/FontHelper#getSmartHeight", new TextProvider(1, HAS_CN_STRING));
    }
    
    private static volatile transient Instrumentation instrumentation;
    
    public static Instrumentation instrumentation() = instrumentation == null ? injectAgent() : instrumentation;
    
    private static synchronized Instrumentation injectAgent() {
        if (instrumentation == null)
            try {
                AgentInjector.inject("sts-fix", LiveInjector.class);
                instrumentation = (Instrumentation) ClassLoader.getSystemClassLoader().loadClass(LiveInjector.class.getName()).getField("instrumentation").get(null);
            } catch (final Exception e) {
                e.printStackTrace();
                throw new InternalError("can't inject instrumentation instance", e);
            }
        return instrumentation;
    }
    
    public static String className(final String name) = name.replace('.', '/');
    
    public static final Logger logger = LogManager.getLogger(Initializer.class);
    
    public static void debug(final String info) = logger.info(info);
    
    public static void initialize() {
        debug("initialize: " + instrumentation());
        instrumentation().addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(final ClassLoader loader, final String name, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte bytecode[]) {
                if (name != null)
                    try {
                        final String className = className(name);
                        if (TEXT_PROViDER_MAPPING.containsKey(className)) {
                            debug((classBeingRedefined != null ? "Retransform" : "Transform") + ": " + name + (classBeingRedefined != null ? "(" + classBeingRedefined.getClassLoader() + ")" : ")"));
                            final ClassNode node = { };
                            final ClassReader reader = { bytecode };
                            reader.accept(node, 0);
                            switch (className) {
                                case "com/megacrit/cardcrawl/events/GenericEventDialog" -> {
                                    final FieldNode _text = new FieldNode(ACC_PUBLIC | ACC_STATIC, "_text", Type.getDescriptor(String.class), null, "");
                                    node.fields.add(_text);
                                    for (final MethodNode method : node.methods)
                                        if (method.name.equals("updateBodyText")) {
                                            final InsnList inject = new InsnList();
                                            inject.add(new VarInsnNode(ALOAD, 1));
                                            inject.add(new FieldInsnNode(PUTSTATIC, node.name, _text.name, _text.desc));
                                            method.instructions.insert(inject);
                                        }
                                }
                            }
                            for (final MethodNode method : node.methods) {
                                final Map<FieldInsnNode, InsnList> fieldsRedirect = new HashMap<>();
                                for (final AbstractInsnNode insnNode : method.instructions) {
                                    if (insnNode instanceof final FieldInsnNode fieldInsnNode) {
                                        if (fieldInsnNode.getOpcode() == GETSTATIC &&
                                                fieldInsnNode.owner.equals(TYPE_SETTINGS) &&
                                                fieldInsnNode.name.equals(FIELD_LINE_BREAK_VIA_CHARACTER)) {
                                            final InsnList redirect = { };
                                            final TextProvider provider = TEXT_PROViDER_MAPPING.get(node.name) ?? TEXT_PROViDER_MAPPING.get(node.name + "#" + method.name);
                                            if (provider == null)
                                                throw new RuntimeException(new NullPointerException("provider: " + node.name + "#" + method.name));
                                            if (provider.text.getClass() == String.class) {
                                                final String fieldName = (String) provider.text;
                                                final FieldNode field = node.fields.stream().filter(fieldNode -> fieldNode.name.equals(fieldName))
                                                        .findAny().orElseThrow(NullPointerException::new);
                                                if ((field.access & ACC_STATIC) != 0) {
                                                    redirect.add(new FieldInsnNode(GETSTATIC, node.name, fieldName, field.desc));
                                                } else {
                                                    redirect.add(new VarInsnNode(ALOAD, 0));
                                                    redirect.add(new FieldInsnNode(GETFIELD, node.name, fieldName, field.desc));
                                                }
                                            } else if (provider.text.getClass() == Integer.class) {
                                                redirect.add(new VarInsnNode(ALOAD, (Integer) provider.text));
                                            } else
                                                throw new RuntimeException("exception text type: " + provider.text.getClass());
                                            redirect.add(provider.handler.clone(Collections.emptyMap()));
                                            fieldsRedirect.put(fieldInsnNode, redirect);
                                        }
                                    }
                                }
                                fieldsRedirect.forEach((fieldInsnNode, insnList) -> {
                                    method.instructions.insert(fieldInsnNode, insnList);
                                    method.instructions.remove(fieldInsnNode);
                                });
                            }
                            final ClassWriter writer = { ClassWriter.COMPUTE_MAXS };
                            node.accept(writer);
                            return writer.toByteArray();
                        }
                    } catch (final Throwable throwable) {
                        throwable.printStackTrace();
                    }
                return null;
            }
        }, true);
        final ClassLoader context = Initializer.class.getClassLoader();
        debug("Context class loader: " + context);
        Stream.of(instrumentation().getAllLoadedClasses())
                .filter(clazz -> clazz.getClassLoader() == context)
                .filter(clazz -> TEXT_PROViDER_MAPPING.containsKey(className(clazz.getName())))
                .forEach(instrumentation()::retransformClasses);
    }
    
}
