package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {
        var importDecl = root.getChildren(IMPORT_DECL);
        var imports = buildImport(importDecl);
        var classDecl = root.getChildren(CLASS_DECL);
        String exName = "";
        SpecsCheck.checkArgument(Kind.CLASS_DECL.check(classDecl.get(0)), () -> "Expected a class declaration: " + classDecl.get(0));
        String className = classDecl.get(0).get("name");
        if (classDecl.get(0).hasAttribute("exName")){
            exName = classDecl.get(0).get("exName");
        }
        var methods = buildMethods(classDecl.get(0));
        var returnTypes = buildReturnTypes(classDecl.get(0));
        var params = buildParams(classDecl.get(0));
        var locals = buildLocals(classDecl.get(0));

        return new JmmSymbolTable(imports, className, exName, methods, returnTypes, params, locals);
    }

    private static List<String> buildImport(List<JmmNode> importDecl) {
        var imports = new ArrayList<String>();
        for (int i = 0; i < importDecl.size(); i++){
            imports.add(importDecl.get(i).getObjectAsList("name").get(importDecl.get(i).getObjectAsList("name").size()-1).toString());
        }
        System.out.println("IMPORTS TYPES: " + imports);
        return imports;
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, Type> map = new HashMap<>();
        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method ->
                    {
                        var returnType = "void";
                        var isArrayOrVargs = false;
                        if (method.get("name").equals("main"));
                        else if (!method.getChildren().isEmpty()) {
                            if(!method.getChildren().get(0).getKind().equals("Array")){
                                returnType = method.getJmmChild(0).getKind().equals("VarArg")? "VarArg" : method.getJmmChild(0).get("name");isArrayOrVargs = method.getChildren().get(0).getKind().equals("Array");
                            } else {
                                returnType = method.getChildren().get(0).getJmmChild(0).get("name");isArrayOrVargs = true;
                            }
                        }
                        //System.out.println("RETURN TYPE: " + returnType);

                        map.put(method.get("name"), new Type(returnType, isArrayOrVargs));
                    });
        System.out.println("RETURN TYPES: " + map);
        return map;

    }


    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();


        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> {
                    var name = method.get("name");

                    if (!method.getChildren("Param").isEmpty()) {
                        // Get the parameters and create a list of symbols
                        var symBols = method.getChildren("Param").stream()
                                .map(param -> new Symbol(
                                        new Type(param.getJmmChild(0).getKind().equals("VarArg")? "VarArg" : param.getJmmChild(0).get("name"), param.getJmmChild(0).getKind().equals("Array")),
                                        param.get("name")))
                                .toList();

                        map.put(name, symBols);
                    } else {
                        map.put(name, name.equals("main") ? List.of(new Symbol(new Type("String", true), method.get("argName"))) : List.of());
                    }


                });
        System.out.println("PARAMS: " + map);
        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();

        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> {
                    var name = method.get("name");
                    var locals = getLocalsList(method);
                    map.put(name, locals);
                });
        var classFields = classDecl.getChildren("VarDecl").stream()
                .map(varDecl -> new Symbol(
                        new Type(varDecl.getJmmChild(0).getKind().equals("VarArg")? "VarArg" : varDecl.getJmmChild(0).get("name"), varDecl.getJmmChild(0).getKind().equals("Array")),
                        varDecl.get("name")))
                .toList();

        map.put(classDecl.get("name"), classFields);


        System.out.println("LOCALS: " + map);
        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> {if(varDecl.getJmmChild(0).getKind().equals("Array"))
                {return new Symbol(new Type(varDecl.getJmmChild(0).getJmmChild(0).get("name"), true), varDecl.get("name"));
                } else {
                    return new Symbol(new Type(varDecl.getJmmChild(0).getKind().equals("VarArg")? "VarArg" : varDecl.getJmmChild(0).get("name"), false), varDecl.get("name"));
                }

                }).toList();
    }



}
