package com.redhat.ceylon.compiler.typechecker.analyzer;

import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class ConstraintVisitor extends Visitor {
    
    private static boolean isIllegalAnnotationParameterType(ProducedType pt) {
        TypeDeclaration ptd = pt.getDeclaration();
        Unit unit = pt.getDeclaration().getUnit();
        if (!ptd.isAnnotation() &&
                !ptd.equals(unit.getStringDeclaration()) &&
                !ptd.equals(unit.getIntegerDeclaration()) &&
                !ptd.equals(unit.getFloatDeclaration()) &&
                !ptd.equals(unit.getCharacterDeclaration()) &&
                !ptd.equals(unit.getIterableDeclaration()) &&
                !ptd.equals(unit.getSequentialDeclaration())) {
            return true;
        }
        if (ptd.equals(unit.getIterableDeclaration()) ||
                ptd.equals(unit.getSequentialDeclaration())) {
            if (isIllegalAnnotationParameterType(unit.getIteratedType(pt))) {
                return true;
            }
        }
        return false;
    }
    
    private void checkAnnotationParameter(Declaration a, Tree.Parameter pn) {
        Parameter p = pn.getDeclarationModel();
        ProducedType pt = p.getType();
        if (isIllegalAnnotationParameterType(pt)) {
            pn.addError("illegal annotation parameter type");
        }
        Tree.DefaultArgument da = pn.getDefaultArgument();
        if (da!=null) {
            Tree.Expression e = da.getSpecifierExpression().getExpression();
            if (e!=null) {
                Tree.Term term = e.getTerm();
                if (term instanceof Tree.Literal) {
                    //ok
                }
                else if (term instanceof Tree.BaseMemberExpression) {
                    Declaration d = ((Tree.BaseMemberExpression) term).getDeclaration();
                    if (d instanceof Parameter) {
                        if (!((Parameter) d).getDeclaration().equals(a)) {
                            e.addError("illegal annotation parameter default argument: must be a reference to a parameter of the annotation");
                        }
                    }
                    else {
                        e.addError("illegal annotation parameter default argument: must be a literal value or parameter reference");
                    }
                }
                else {
                    e.addError("illegal annotation parameter default argument: must be a literal value or parameter reference");
                }
            }
        }
    }

    private TypeDeclaration getAnnotationDeclaration(Unit unit) {
        return (TypeDeclaration) unit.getPackage().getModule()
                .getLanguageModule()
                .getDirectPackage("ceylon.language.metamodel")
                .getMemberOrParameter(unit, "Annotation", null, false);
    }

    @Override
    public void visit(Tree.AnyClass that) {
        super.visit(that);
        Class c = that.getDeclarationModel();
        if (c.isAnnotation()) {
            if (c.isParameterized()) {
                that.addError("annotation class may not be a parameterized type");
            }
            if (c.isAbstract()) {
                that.addError("annotation class may not be abstract");
            }
            if (!c.getExtendedTypeDeclaration()
                    .equals(that.getUnit().getBasicDeclaration())) {
                that.addError("annotation class must directly extend Basic");
            }
            TypeDeclaration annotationDec = getAnnotationDeclaration(that.getUnit());
            if (!c.inherits(annotationDec)) {
                that.addError("annotation class must be a subtype of Annotation");
            }
            for (Tree.Parameter pn: that.getParameterList().getParameters()) {
                checkAnnotationParameter(c, pn);
            }
        }
    }

    @Override
    public void visit(Tree.AnyMethod that) {
        super.visit(that);
        Method a = that.getDeclarationModel();
        if (a.isAnnotation()) {
            Tree.Type type = that.getType();
            if (type!=null) {
                if (!type.getTypeModel().getDeclaration().isAnnotation()) {
                    type.addError("annotation constructor must return an annotation type");
                }
            }
            List<Tree.ParameterList> pls = that.getParameterLists();
            if (pls.size() == 1) {
                for (Tree.Parameter pn: pls.get(0).getParameters()) {
                    checkAnnotationParameter(a, pn);
                }
            }
            else {
                that.addError("annotation constructor must have exactly one parameter list");
            }
        }
    }
    
    @Override public void visit(Tree.Annotation that) {
        super.visit(that);
        Declaration dec = ((Tree.MemberOrTypeExpression) that.getPrimary()).getDeclaration();
        /*if (dec!=null && !dec.isToplevel()) {
            that.getPrimary().addError("annotation must be a toplevel function reference");
        }*/
        if (dec!=null && !dec.isAnnotation()) {
            that.getPrimary().addError("not an annotation constructor");
        }
    }
    
}