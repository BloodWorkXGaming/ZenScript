package stanhebben.zenscript.impl;

import stanhebben.zenscript.*;
import stanhebben.zenscript.annotations.ZenExpansion;
import stanhebben.zenscript.compiler.*;
import stanhebben.zenscript.parser.Token;
import stanhebben.zenscript.symbols.*;
import stanhebben.zenscript.type.ZenTypeNative;
import stanhebben.zenscript.type.natives.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.ToIntFunction;

public class GenericRegistry implements IZenRegistry {
    
    private IZenCompileEnvironment compileEnvironment;
    
    private Map<String, IZenSymbol> globals = new HashMap<>();
    private Set<IBracketHandler> bracketHandlers = new TreeSet<>(Comparator.comparingInt((ToIntFunction<IBracketHandler>) IBracketHandler::getPriority).thenComparing(o -> o.getClass().getName()));
    private TypeRegistry types = new TypeRegistry();
    private SymbolPackage root = new SymbolPackage("<root>");
    private Map<String, TypeExpansion> expansions = new HashMap<>();
    private IZenErrorLogger errorLogger;
    private IZenLogger logger = new GenericLogger();
    
    public GenericRegistry(IZenCompileEnvironment compileEnvironment, IZenErrorLogger errorLogger) {
        this.compileEnvironment = compileEnvironment;
        this.errorLogger = errorLogger;
        this.compileEnvironment.setRegistry(this);
    }
    
    public void registerGlobal(String name, IZenSymbol symbol) {
        if(globals.containsKey(name)) {
            throw new IllegalArgumentException("symbol already exists: " + name);
        }
        
        globals.put(name, symbol);
    }
    
    public void registerExpansion(Class<?> cls) {
        try {
            for(Annotation annotation : cls.getAnnotations()) {
                if(annotation instanceof ZenExpansion) {
                    ZenExpansion eAnnotation = (ZenExpansion) annotation;
                    if(!expansions.containsKey(eAnnotation.value())) {
                        expansions.put(eAnnotation.value(), new TypeExpansion(eAnnotation.value()));
                    }
                    expansions.get(eAnnotation.value()).expand(cls, types);
                }
            }
        } catch(Throwable ex) {
            ex.printStackTrace();
        }
    }
    
    public void registerBracketHandler(IBracketHandler handler) {
        bracketHandlers.add(handler);
    }
    
    public void removeBracketHandler(IBracketHandler handler) {
        IBracketHandler toRemove = null;
        for(IBracketHandler bracket : bracketHandlers) {
            if(bracket.equals(handler)) {
                toRemove = bracket;
            }
        }
        bracketHandlers.remove(toRemove);
    }
    
    public void registerNativeClass(Class<?> cls) {
        try {
            ZenTypeNative type = new ZenTypeNative(cls);
            type.complete(types);
            root.put(type.getName(), new SymbolType(type), errorLogger);
        } catch(Throwable ex) {
            ex.printStackTrace();
        }
    }
    
    public void registerAdapter(Class clazz, String pack, Method... methods){
        if (pack.isEmpty()) {
            pack = "<root>";
        }
        
        ZenTypeNative type = new ZenTypeNative(clazz);
        type.complete(types);
        getRoot().put(pack + clazz.getSimpleName(), new SymbolType(type), errorLogger);
        
        for(Method method : methods) {
            if(Modifier.isPublic(method.getModifiers())) {
                ZenNativeMember member = new ZenNativeMember();
                member.addMethod(new JavaMethod(method, types));
                
                type.getStaticMembers().put(method.getName(), member);
            }
        }
    }
    
    public IZenSymbol getStaticFunction(Class cls, String name, Class... arguments) {
        IJavaMethod method = JavaMethod.get(types, cls, name, arguments);
        return new SymbolJavaStaticMethod(method);
    }
    
    public IZenSymbol getStaticField(Class cls, String name) {
        try {
            Field field = cls.getDeclaredField(name);
            return new SymbolJavaStaticField(cls, field, types);
        } catch(NoSuchFieldException | SecurityException ex) {
            getLogger().error("Unable to get static field: " + name + " from class " + cls.getName(), ex);
            return null;
        }
    }
    
    public IZenSymbol resolveBracket(IEnvironmentGlobal environment, List<Token> tokens) {
        int tokensSize = tokens.size();
        if (tokensSize <= 0) return null;
        
        String s = tokens.get(0).getValue();
        for(int i = 1; i < tokensSize; i++) {
            s = s.concat(tokens.get(i).getValue());
        }
        
        for(IBracketHandler bracketHandler : bracketHandlers) {
            if (!bracketHandler.getRegexPattern().matcher(s).matches()) continue;
            
            IZenSymbol symbol = bracketHandler.resolve(environment, tokens);
            if(symbol != null) {
                return symbol;
            }
        }
        
        return null;
    }
    
    public IEnvironmentGlobal makeGlobalEnvironment(Map<String, byte[]> classes) {
        return new GenericGlobalEnvironment(classes, this);
    }
    
    public IZenCompileEnvironment getCompileEnvironment() {
        return compileEnvironment;
    }
    
    public Map<String, IZenSymbol> getGlobals() {
        return globals;
    }
    
    public Set<IBracketHandler> getBracketHandlers() {
        return bracketHandlers;
    }
    
    public TypeRegistry getTypes() {
        return types;
    }
    
    public SymbolPackage getRoot() {
        return root;
    }
    
    public Map<String, TypeExpansion> getExpansions() {
        return expansions;
    }
    
    public void setCompileEnvironment(IZenCompileEnvironment compileEnvironment) {
        this.compileEnvironment = compileEnvironment;
    }
    
    public void setGlobals(Map<String, IZenSymbol> globals) {
        this.globals = globals;
    }
    
    public void setBracketHandlers(Set<IBracketHandler> bracketHandlers) {
        this.bracketHandlers = bracketHandlers;
    }
    
    public void setTypes(TypeRegistry types) {
        this.types = types;
    }
    
    public void setRoot(SymbolPackage root) {
        this.root = root;
    }
    
    public void setExpansions(Map<String, TypeExpansion> expansions) {
        this.expansions = expansions;
    }
    
    public IZenErrorLogger getErrorLogger() {
        return errorLogger;
    }
    
    public void setErrorLogger(IZenErrorLogger errorLogger) {
        this.errorLogger = errorLogger;
    }
    
    public IZenLogger getLogger() {
        return logger;
    }
    
    public void setLogger(IZenLogger logger) {
        this.logger = logger;
    }
}
