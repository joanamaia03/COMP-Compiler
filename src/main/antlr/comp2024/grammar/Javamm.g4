grammar Javamm;

@header {
    package pt.up.fe.comp2024;
}

EQUALS : '=';
SEMI : ';' ;
COMMA : ',';
LCURLY : '{' ;
RCURLY : '}' ;
LPAREN : '(' ;
RPAREN : ')' ;
LSQUARE : '[' ;
RSQUARE : ']' ;
MUL : '*' ;
ADD : '+' ;
POINT : '.' ;
DOTS : '...' ;
EXCL : '!' ;

CLASS : 'class' ;
INT : 'int' ;
PUBLIC : 'public' ;
RETURN : 'return' ;
IMPORT : 'import' ;
EXTENDS : 'extends' ;
STATIC : 'static' ;
VOID : 'void' ;
STRING : 'String' ;
BOOL : 'boolean' ;
IF : 'if' ;
ELSE : 'else' ;
WHILE : 'while' ;
LENGTH : 'length' ;
NEW : 'new' ;
THIS : 'this' ;
TRUE : 'true' ;
FALSE : 'false' ;

INTEGER : [0] | [1-9]([0-9])* ;
ID : [a-zA-Z_$]([a-zA-Z0-9_$])*;

MULTI_COMMENT : '/*' .*? '*/' -> skip;
END_COMMENT : '//' .*? '\n'-> skip;
WS : [ \t\n\r\f]+ -> skip ;

program
    : classDecl EOF
    | (importDecl)* classDecl EOF
    ;

importDecl
    : IMPORT name+=(ID | 'main')
        (POINT name+=(ID | 'main'))*SEMI
    ;

classDecl
    : CLASS name=(ID | 'main')
        (EXTENDS exName=(ID | 'main'))? LCURLY (varDecl)* (methodDecl)* RCURLY
    ;

varDecl
    : type name=ID SEMI
    | type name=LENGTH SEMI
    | type name='main' SEMI
    ;

returnStmt
    : RETURN expr SEMI
    ;

type
    : type LSQUARE RSQUARE #Array
    | name=INT DOTS #VarArg
    | name=BOOL #Boolean
    | name=INT #Int
    | name=ID #Id
    | name='main'#Id
    | name=STRING #String
    | name=VOID #Void
    ;

methodDecl locals[boolean isPublic=false]
    : (PUBLIC {$isPublic=true;})? type name=ID LPAREN (param (COMMA param)*)? RPAREN
        LCURLY (varDecl)* (stmt)* returnStmt RCURLY
      | (PUBLIC {$isPublic=true;})? STATIC VOID name = 'main' LPAREN STRING LSQUARE
        RSQUARE argName=ID RPAREN LCURLY (varDecl)* (stmt)* RCURLY
    ;


param
    : type name=(ID | 'main')
    ;

stmt
    : LCURLY (stmt)* RCURLY #CurlyStmt
    | IF LPAREN expr RPAREN stmt ELSE stmt #IfStmt
    | WHILE LPAREN expr RPAREN stmt #WhileStmt
    | expr SEMI #ExprStmt
    | expr EQUALS expr SEMI #AssignStmt
    | expr LSQUARE expr RSQUARE EQUALS expr SEMI #AssignArrayStmt
    ;

expr
    : expr POINT value=(ID | 'main') LPAREN (expr(COMMA expr)*)? RPAREN #FunctionExpr
    | LPAREN expr RPAREN #ParenExpr
    | expr op= ('*' | '/') expr #BinaryExpr
    | expr op= ('+' | '-') expr #BinaryExpr
    | expr op= '<' expr #BinaryExpr
    | expr op='&&' expr #BinaryExpr
    | expr LSQUARE expr RSQUARE #ArrayAccess
    | expr POINT LENGTH #LengthExpr
    | NEW name=INT LSQUARE expr RSQUARE #NewArrayExpr
    | NEW className=(ID | 'main') LPAREN RPAREN #NewClassExpr
    | value=EXCL expr #NegExpr
    | LSQUARE (expr(COMMA expr)*)? RSQUARE #ArrayExpr
    | value=INTEGER #IntegerLiteral
    | value=TRUE #BoolLiteral
    | value=FALSE #BoolLiteral
    | name=ID #VarRefExpr
    | name=LENGTH #VarRefExpr
    | name='main' #VarRefExpr
    | value= THIS #ThisExpr
    ;


