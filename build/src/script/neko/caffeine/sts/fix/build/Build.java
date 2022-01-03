package neko.caffeine.sts.fix.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

import org.objectweb.asm.commons.ClassRemapper;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.build.IDEA;
import amadeus.maho.util.build.Jar;
import amadeus.maho.util.build.Javac;
import amadeus.maho.util.build.Module;
import amadeus.maho.util.build.Workspace;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.remap.RemapHandler;
import amadeus.maho.util.depend.Project;
import amadeus.maho.util.depend.Repository;
import amadeus.maho.util.runtime.FileHelper;

@SneakyThrows
public interface Build {
    
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class STSConfig {
        
        String stsPath = "missing", desktopJar = "desktop-1.0.jar";
        
    }
    
    Workspace workspace = Workspace.here();
    
    STSConfig config = workspace.config().load(new STSConfig()).let(it -> {
        if (!Files.isDirectory(Path.of(it.stsPath)))
            throw new IllegalArgumentException("STSConfig.default.cfg # invalid stsPath: " + it.stsPath);
    });
    
    Path stsPath = Path.of(config.stsPath), desktopJar = stsPath / config.desktopJar, libs = (workspace.root() / "libs").toAbsolutePath(), mtsJar = libs / "ModTheSpire.jar", mods = stsPath / "mods";
    
    Module.SingleDependency desktop = { desktopJar }, mts = { mtsJar };
    
    Repository maven = Repository.maven();
    
    Set<Module.Dependency> asm = maven.resolveModuleDependencies(new Project.Dependency.Holder().all("org.ow2.asm:asm:+", "org.ow2.asm:asm-tree:+").dependencies());
    
    Module module = { "sts.fix", new HashSet<>(asm) *= List.of(desktop, mts) };
    
    static void sync() {
        IDEA.deleteLibraries(workspace);
        IDEA.generateAll(workspace, "17", true, List.of(Module.build(), module));
    }
    
    static void build() {
        workspace.clean(module).flushMetadata();
        Javac.compile(workspace, module, _ -> true, args -> args += "-XDstringConcat=inline");
        final Path classes = workspace.output(Javac.CLASSES_DIR, module) / module.name(), shadow = classes / "neko/caffeine/sts/fix";
        (classes / Jar.MODULE_INFO)--;
        Files.walkFileTree(classes, FileHelper.visitor(
                (path, _) -> Files.write(path, Files.readAllBytes(path).let(bytecode -> bytecode[7] = 52)),
                (path, _) -> path.toString().endsWith(Javac.CLASS_SUFFIX)));
        workspace.root() / module.path() / "src" / module.name() / "ModTheSpire.json" >> classes;
        final RemapHandler.ASMRemapper remapper = new RemapHandler() {
            @Override
            public String mapInternalName(final String name) = name.startsWith("org/objectweb/asm") ? name.replace("org/objectweb/asm", "neko/caffeine/sts/fix/org/objectweb/asm") : name;
        }.remapper();
        final BiConsumer<Path, UnaryOperator<Path>> consumer = (root, mapper) -> Files.walkFileTree(root, FileHelper.visitor(
                (path, _) -> ClassWriter.toBytecode(visitor -> ASMHelper.newClassReader(Files.readAllBytes(path)).accept(new ClassRemapper(visitor, remapper), 0)) >> mapper.apply(path).let(it -> ~-it),
                (path, _) -> path.toString().endsWith(Javac.CLASS_SUFFIX) && !path.getFileName().toString().equals(Jar.MODULE_INFO)));
        consumer.accept(classes, UnaryOperator.identity());
        asm.stream().flatMap(Module.Dependency::flat).forEach(dependency -> dependency.classes() | root -> consumer.accept(root, path -> shadow / (root % path).toString()));
        Jar.pack(workspace, module);
    }
    
    static void push() {
        build();
        workspace.output(Jar.MODULES_DIR, module) >> mods;
    }
    
}
