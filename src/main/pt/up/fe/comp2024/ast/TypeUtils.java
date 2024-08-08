package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.List;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";

    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {
        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case PAREN_EXPR -> getExprType(expr.getChildren().get(0), table);
            case ARRAY_ACCESS -> new Type(getExprType(expr.getChildren().get(0),table).getName(),false);
            case LENGTH_EXPR -> new Type("int", false);
            case FUNCTION_EXPR -> table.getReturnType(expr.get("value"));
            case ARRAY_EXPR, NEW_ARRAY_EXPR -> new Type("int", true);
            case NEW_CLASS_EXPR -> new Type(expr.get("className"),false);
            case NEG_EXPR, BOOL_LITERAL -> new Type("boolean", false);
            case THIS_EXPR ->  new Type(table.getClassName(), false);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }

    private static Type getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/" -> new Type(INT_TYPE_NAME, false);
            case "<", "&&" -> new Type("boolean", false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    public static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        JmmNode temp = varRefExpr;
        while(!temp.getKind().equals("MethodDecl")){
            temp = temp.getParent();
        }
        List<Symbol> localVars = table.getLocalVariables(temp.get("name"));
        List<Symbol> parameters = table.getParameters(temp.get("name"));
        List<Symbol> fields = table.getFields();
        List<String> imports = table.getImports();
        for(Symbol x: localVars) if(x.getName().equals(varRefExpr.get("name"))) return x.getType();
        for(Symbol x: parameters) if(x.getName().equals(varRefExpr.get("name"))) return x.getType();
        for(Symbol x: fields) if(x.getName().equals(varRefExpr.get("name"))) return x.getType();
        for(String x: imports) if(x.equals(varRefExpr.get("name"))) return new Type(varRefExpr.getKind(), false);
        return new Type("Undefined", false);
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        return sourceType.equals(destinationType);
    }
}
