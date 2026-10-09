"""Execute the real Fabric registry binding against small native API doubles."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
FAMILY = ROOT / 'source-families/26.X/src/main/java'
FABRIC = ROOT / 'source-family-platforms/26.X/fabric/src/main/java'


class FabricRegistryBindingTest(unittest.TestCase):
    def test_deferred_registration_and_failure_boundaries(self):
        javac = shutil.which('javac')
        java = shutil.which('java')
        if not javac or not java:
            self.fail('javac and java are required for the Fabric registry probe')
        stubs = {
            'net/minecraft/resources/Identifier.java': '''package net.minecraft.resources;
public record Identifier(String namespace, String path) {
 public static Identifier parse(String id) { String[] parts=id.split(":",2); return new Identifier(parts[0],parts[1]); }
 public static Identifier fromNamespaceAndPath(String ns,String path) { return new Identifier(ns,path); }
 public String toString() { return namespace+":"+path; }
}''',
            'net/minecraft/resources/ResourceKey.java': '''package net.minecraft.resources;
import net.minecraft.core.Registry;
public record ResourceKey<T>(Identifier identifier) {
 public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier id) { return new ResourceKey<>(id); }
}''',
            'net/minecraft/core/Registry.java': '''package net.minecraft.core;
import java.util.*; import net.minecraft.resources.*;
public final class Registry<T> implements Iterable<T> {
 public final Map<Identifier,T> entries=new LinkedHashMap<>();
 public Optional<T> getOptional(Identifier id) { return Optional.ofNullable(entries.get(id)); }
 public boolean containsKey(Identifier id) { return entries.containsKey(id); }
 public String key() { return "probe"; }
 public Iterator<T> iterator() { return entries.values().iterator(); }
 public static <V,T extends V> T register(Registry<V> registry,Identifier id,T value) {
  if(registry.entries.putIfAbsent(id,value)!=null) throw new IllegalStateException("duplicate"); return value;
 }
}''',
            'net/minecraft/core/registries/BuiltInRegistries.java': '''package net.minecraft.core.registries;
import net.minecraft.core.Registry;
public final class BuiltInRegistries { public static final Registry<Registry<?>> REGISTRY=new Registry<>(); }''',
            'net/fabricmc/fabric/api/event/registry/RegistryAttribute.java': '''package net.fabricmc.fabric.api.event.registry;
public enum RegistryAttribute { MODDED }''',
            'net/fabricmc/fabric/api/event/registry/RegistryAttributeHolder.java': '''package net.fabricmc.fabric.api.event.registry;
import net.minecraft.core.Registry;
public final class RegistryAttributeHolder {
 public static RegistryAttributeHolder get(Registry<?> registry) { return new RegistryAttributeHolder(); }
 public RegistryAttributeHolder addAttribute(RegistryAttribute attribute) { return this; }
}''',
            'probe/Main.java': '''package probe;
import java.util.*; import buildcraft.lib.platform.registry.*;
import net.minecraft.core.*; import net.minecraft.core.registries.*; import net.minecraft.resources.*;
public final class Main {
 static void check(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
 static void fails(Class<? extends Throwable> type,Runnable action,String message) {
  try { action.run(); } catch(Throwable cause) { if(type.isInstance(cause)) return; throw new AssertionError(message,cause); }
  throw new AssertionError(message);
 }
 public static void main(String[] args) {
  Registry<String> blocks=new Registry<>();
  Registry.register(BuiltInRegistries.REGISTRY,Identifier.parse("minecraft:block"),blocks);
  Registry<String> registry=new Registry<>();
  Registry.register(BuiltInRegistries.REGISTRY,Identifier.parse("minecraft:item"),registry);
  List<String> calls=new ArrayList<>();
  BCDeferredRegister<String> first=BCDeferredRegister.create("minecraft:item","buildcraftcore");
  var a=first.register("a",()-> { calls.add("a"); return "a"; });
  var b=first.register("b",()-> { calls.add("b"); check(a.get().equals("a"),"dependency unavailable"); return "b"; });
  RegistryBinding binding=RegistryBinding.on(); first.register(binding);
  BCDeferredRegister<String> blockCatalog=BCDeferredRegister.create("minecraft:block","buildcraftcore");
  var block=blockCatalog.register("block",()-> { calls.add("block"); return "block"; }); blockCatalog.register(binding);
  check(calls.isEmpty(),"binding evaluated a factory"); check(!a.isPresent(),"entry present too early");
  fails(IllegalStateException.class,a::get,"early read succeeded");
  fails(IllegalStateException.class,()->first.register(binding),"duplicate binding succeeded");
  fails(IllegalArgumentException.class,()->first.register("a",()->"duplicate"),"duplicate ID succeeded");
  var c=first.register("c",()-> { calls.add("c"); return "c"; });
  BCDeferredRegister<String> second=BCDeferredRegister.create("minecraft:item","buildcrafttransport");
  second.register("d",()-> { calls.add("d"); return "d"; }); second.register(binding);
  binding.registerAll();
  check(calls.equals(List.of("block","a","b","c","d")),"registration order changed: "+calls);
  check(block.isBound(),"block dependency unavailable");
  check(a.isBound() && b.isBound() && c.get().equals("c"),"entries did not bind");
  fails(IllegalStateException.class,binding::registerAll,"flush repeated");
  fails(IllegalStateException.class,()->first.register("late",()->"late"),"late registration accepted");
  check(first.entries().size()==3,"failed late registration left a phantom descriptor");
  RegistryBinding broken=RegistryBinding.on();
  BCDeferredRegister<String> failure=BCDeferredRegister.create("minecraft:item","buildcraftlib");
  var nullEntry=failure.register("null",()->(String)null); failure.register(broken);
  fails(IllegalStateException.class,broken::registerAll,"null factory accepted");
  check(!nullEntry.isBound(),"failed entry became bound");
  fails(IllegalStateException.class,broken::registerAll,"failed flush retried partial native mutation");
  RegistryBinding duplicate=RegistryBinding.on();
  BCDeferredRegister<String> collision=BCDeferredRegister.create("minecraft:item","buildcraftcore");
  collision.register("a",()-> { throw new AssertionError("duplicate factory executed"); }); collision.register(duplicate);
  fails(IllegalStateException.class,duplicate::registerAll,"native duplicate accepted");
  BCDeferredRegister<String> unknown=BCDeferredRegister.create("minecraft:unknown","buildcraftcore");
  fails(IllegalArgumentException.class,()->unknown.register(RegistryBinding.on()),"unknown registry accepted");
  System.out.println("Fabric deferred registration probe passed");
 }
}''',
        }
        with tempfile.TemporaryDirectory(prefix='bc-fabric-registry-') as directory:
            work = Path(directory)
            for relative, source in stubs.items():
                path = work / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source, encoding='utf-8')
            sources = list(work.rglob('*.java'))
            for name in ('BCDeferredRegister', 'BCRegistryEntry', 'BCRegistryBinder', 'RegistryNames'):
                sources.append(FAMILY / f'buildcraft/lib/platform/registry/{name}.java')
            sources.append(FABRIC / 'buildcraft/lib/platform/registry/RegistryBinding.java')
            compiled = subprocess.run([javac, '-d', str(work / 'classes'), *map(str, sources)],
                                      capture_output=True, text=True, check=False)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            result = subprocess.run([java, '-cp', str(work / 'classes'), 'probe.Main'],
                                    capture_output=True, text=True, check=False)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn('probe passed', result.stdout)


if __name__ == '__main__':
    unittest.main()
