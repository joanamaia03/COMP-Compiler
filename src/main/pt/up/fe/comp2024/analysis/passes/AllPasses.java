package pt.up.fe.comp2024.analysis.passes;

import jas.Var;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.Collections;
import java.util.List;

import static pt.up.fe.comp2024.ast.TypeUtils.areTypesAssignable;
import static pt.up.fe.comp2024.ast.TypeUtils.getExprType;

public class AllPasses extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.NEW_CLASS_EXPR, this::visitNewClassExpr);
        addVisit(Kind.FUNCTION_EXPR, this::visitFunction);
        addVisit(Kind.BINARY_EXPR, this::visitBinary);
        addVisit(Kind.IF_STMT, this::visitIf);
        addVisit(Kind.WHILE_STMT, this::visitWhile);
        addVisit(Kind.ASSIGN_STMT, this::visitAssign);
        addVisit(Kind.ARRAY_EXPR, this::visitArray);
        addVisit(Kind.ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(Kind.RETURN_STMT, this::visitReturn);
        addVisit(Kind.PROGRAM, this::visitProgram);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitNewClassExpr(JmmNode ClassExpr, SymbolTable table) {
        List<String> methods = table.getMethods();
        List<String> imports = table.getImports();
        if (methods.contains(ClassExpr.get("className")) || table.getClassName().equals(ClassExpr.get("className")) || imports.contains(ClassExpr.get("className")))
            return null;
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(ClassExpr),
                NodeUtils.getColumn(ClassExpr),
                "Couldn't find class",
                null)
        );
        return null;
    }

    private Void visitFunction(JmmNode FunctionExpr, SymbolTable table) {
        Type functionClass = getExprType(FunctionExpr.getChildren().get(0), table);
        List<String> imports = table.getImports();
        List<String> methods = table.getMethods();
        JmmNode temp = FunctionExpr;
        while (!temp.getKind().equals("MethodDecl")) {
            temp = temp.getParent();
        }
        if (FunctionExpr.getChildren().get(0).getKind().equals("ThisExpr") && temp.get("name").equals("main"))
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(FunctionExpr),
                    NodeUtils.getColumn(FunctionExpr),
                    "Can't use \"this\" inside main method",
                    null)
            );
        if (imports.contains(FunctionExpr.get("value")) || imports.contains(functionClass.getName()) || (table.getClassName().equals(functionClass.getName()) && imports.contains(table.getSuper())) || (functionClass.getName().equals("VarRefExpr") && imports.contains(FunctionExpr.getChildren().get(0).get("name"))))
            return null;
        if (methods.contains(FunctionExpr.get("value")) && table.getClassName().equals(functionClass.getName())) {
            List<Symbol> parameters = table.getParameters(FunctionExpr.get("value"));
            if (parameters.size() == FunctionExpr.getChildren().size() - 1) {
                boolean check = true;
                for (int x = 0; x < parameters.size(); x++)
                    if (!getExprType(FunctionExpr.getChildren().get(x + 1), table).equals(parameters.get(x).getType()))
                        check = false;
                if (check) return null;
            }

            if (parameters.get(parameters.size() - 1).getType().getName().equals("VarArg") && parameters.size() <= FunctionExpr.getChildren().size() - 1) {
                boolean check = true;
                for (int x = 0; x < parameters.size() - 1; x++) {
                    if (!getExprType(FunctionExpr.getChildren().get(x + 1), table).getName().equals("int"))
                        check = false;
                }
                if (check) return null;
            }
        }
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(FunctionExpr),
                NodeUtils.getColumn(FunctionExpr),
                "Function doesn't exist or has wrong parameters",
                null)
        );
        return null;
    }

    private Void visitBinary(JmmNode BinaryExpr, SymbolTable table) {
        if (getExprType(BinaryExpr.getChildren().get(0), table).getName().equals("Undefined") || getExprType(BinaryExpr.getChildren().get(1), table).getName().equals("Undefined"))
            return null;
        if (!getExprType(BinaryExpr.getChildren().get(0), table).getName().equals(getExprType(BinaryExpr.getChildren().get(1), table).getName()) || getExprType(BinaryExpr.getChildren().get(0), table).isArray() != getExprType(BinaryExpr.getChildren().get(1), table).isArray()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(BinaryExpr),
                    NodeUtils.getColumn(BinaryExpr),
                    "Operands must be of the same type",
                    null)
            );
        }
        if ((BinaryExpr.get("op").equals("&&")) && (!getExprType(BinaryExpr.getChildren().get(0), table).getName().equals("boolean") || !getExprType(BinaryExpr.getChildren().get(1), table).getName().equals("boolean"))) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(BinaryExpr),
                    NodeUtils.getColumn(BinaryExpr),
                    "Operands must be boolean while using operator" + BinaryExpr.get("op"),
                    null)
            );
        } else if ((getExprType(BinaryExpr.getChildren().get(0), table).isArray() || getExprType(BinaryExpr.getChildren().get(1), table).isArray())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(BinaryExpr),
                    NodeUtils.getColumn(BinaryExpr),
                    "Cannot use arrays in arithmetic operations",
                    null)
            );
        }
        return null;
    }

    private Void visitIf(JmmNode IfStmt, SymbolTable table) {
        if (getExprType(IfStmt.getChildren().get(0), table).getName().equals("boolean")) return null;
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(IfStmt),
                NodeUtils.getColumn(IfStmt),
                "Expression in if must be boolean",
                null)
        );
        return null;
    }

    private Void visitWhile(JmmNode WhileStmt, SymbolTable table) {
        if (getExprType(WhileStmt.getChildren().get(0), table).getName().equals("boolean")) return null;
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(WhileStmt),
                NodeUtils.getColumn(WhileStmt),
                "Expression in while must be boolean",
                null)
        );
        return null;
    }

    private Void visitReturn(JmmNode ReturnStmt, SymbolTable table) {
        JmmNode temp = ReturnStmt;
        while (!temp.getKind().equals("MethodDecl")) {
            temp = temp.getParent();
        }
        if (getExprType(ReturnStmt.getChildren().get(0),table).getName().equals("VarArg") && !ReturnStmt.getChildren().get(0).getKind().equals("ArrayAccess")){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(ReturnStmt),
                    NodeUtils.getColumn(ReturnStmt),
                    "Function return type can't be VarArg",
                    null)
            );
        }
        if (table.getReturnType(temp.get("name")).getName().equals("VarArg")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(ReturnStmt),
                    NodeUtils.getColumn(ReturnStmt),
                    "Function return type can't be VarArg",
                    null)
            );
        }
        if (getExprType(ReturnStmt.getChildren().get(0), table).getName().equals(table.getReturnType(temp.get("name")).getName()) || (getExprType(ReturnStmt.getChildren().get(0), table).getName().equals("int") && table.getReturnType(temp.get("name")).getName().equals("VarArg")) || (getExprType(ReturnStmt.getChildren().get(0), table).getName().equals("VarArg") && table.getReturnType(temp.get("name")).getName().equals("int")))
            return null;
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(ReturnStmt),
                NodeUtils.getColumn(ReturnStmt),
                "Return must have same type as function",
                null)
        );
        return null;
    }

    private Void visitAssign(JmmNode AssignStmt, SymbolTable table) {
        JmmNode temp = AssignStmt;
        while (!temp.getKind().equals("MethodDecl")) {
            temp = temp.getParent();
        }
        for(Symbol s : table.getLocalVariables(temp.get("name"))){
            if (temp.get("name").equals("main") && s.getName().equals(AssignStmt.getChildren().get(0).get("name"))) return null;
        }
        for(Symbol s : table.getFields()){
            if (temp.get("name").equals("main") && s.getName().equals(AssignStmt.getChildren().get(0).get("name")))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(AssignStmt),
                        NodeUtils.getColumn(AssignStmt),
                        "Can't use fields inside main method",
                        null)
                );
        }
        List<String> imports = table.getImports();
        if (areTypesAssignable(getExprType(AssignStmt.getChildren().get(0), table), getExprType(AssignStmt.getChildren().get(1), table)))
            return null;
        if (imports.contains(getExprType(AssignStmt.getChildren().get(0), table).getName()) && imports.contains(getExprType(AssignStmt.getChildren().get(1), table).getName()))
            return null;
        else if (!AssignStmt.getChildren().get(1).getChildren().isEmpty() && imports.contains(getExprType(AssignStmt.getChildren().get(1).getChildren().get(0), table).getName()))
            return null;
        if (getExprType(AssignStmt.getChildren().get(0), table).getName().equals(table.getSuper()) && getExprType(AssignStmt.getChildren().get(1), table).getName().equals(table.getClassName()))
            return null;
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(AssignStmt),
                NodeUtils.getColumn(AssignStmt),
                "Can't assign variables with value of different types",
                null)
        );
        return null;
    }

    private Void visitArray(JmmNode ArrayExpr, SymbolTable table) {
        for (JmmNode child : ArrayExpr.getChildren()) {
            if (!(getExprType(child, table).getName().equals("int") || getExprType(child, table).getName().equals("VarArg"))) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(ArrayExpr),
                        NodeUtils.getColumn(ArrayExpr),
                        "Array values must be integer",
                        null)
                );
                return null;
            }
        }
        return null;
    }

    private Void visitArrayAccess(JmmNode ArrayAccessExpr, SymbolTable table) {
        if (getExprType(ArrayAccessExpr.getChildren().get(0), table).equals(new Type("int", true)) && getExprType(ArrayAccessExpr.getChildren().get(1), table).getName().equals("int"))
            return null;
        if (getExprType(ArrayAccessExpr.getChildren().get(0), table).equals(new Type("VarArg", false))) return null;
        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(ArrayAccessExpr),
                NodeUtils.getColumn(ArrayAccessExpr),
                "Array access is invalid",
                null)
        );
        return null;
    }

    private Void visitProgram(JmmNode ProgramExpr, SymbolTable table) {
        List<String> imports = table.getImports();
        for (String x : imports)
            if (Collections.frequency(imports, x) > 1)
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(ProgramExpr),
                        NodeUtils.getColumn(ProgramExpr),
                        "Duplicated import",
                        null)
                );
        List<String> methods = table.getMethods();
        for (String x : methods)
            if (Collections.frequency(methods, x) > 1)
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(ProgramExpr),
                        NodeUtils.getColumn(ProgramExpr),
                        "Duplicated method",
                        null)
                );
        List<Symbol> fields = table.getFields();
        for (Symbol x : fields) {
            if (Collections.frequency(fields, x) > 1)
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(ProgramExpr),
                        NodeUtils.getColumn(ProgramExpr),
                        "Duplicated field",
                        null)
                );
            if (x.getType().getName().equals("VarArg"))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(ProgramExpr),
                        NodeUtils.getColumn(ProgramExpr),
                        "Field can't be VarArg",
                        null)
                );
        }
        return null;
    }
    private Void visitVarDecl(JmmNode VarDecl, SymbolTable table) {
        if (VarDecl.getChildren().get(0).getKind().equals("Array") && !VarDecl.getChildren().get(0).getChildren().get(0).get("name").equals("int")){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(VarDecl),
                    NodeUtils.getColumn(VarDecl),
                    "Array must be int",
                    null)
            );
        }
        return null;
    }
}