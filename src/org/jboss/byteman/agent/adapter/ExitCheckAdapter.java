package org.jboss.byteman.agent.adapter;

import org.objectweb.asm.*;
import org.jboss.byteman.rule.Rule;

import java.util.Vector;

/**
 * asm Adapter class used to check that the target method for a rule exists in a class
 */
public class ExitCheckAdapter extends RuleCheckAdapter
{
    public ExitCheckAdapter(ClassVisitor cv, Rule rule, String targetClass, String targetMethod) {
        super(cv, rule, targetClass, targetMethod);
        this.earlyReturnHandlers = new Vector<Label>();
    }
    /**
     * table used to track which returns have been added because of exception handling code
     */

    private Vector<Label> earlyReturnHandlers;

    public MethodVisitor visitMethod(
        final int access,
        final String name,
        final String desc,
        final String signature,
        final String[] exceptions)
    {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (matchTargetMethod(name, desc)) {
            return new ExitCheckMethodAdapter(mv, rule, access, name, desc, signature, exceptions);
        }

        return mv;
    }

    /**
     * a method visitor used to add a rule event trigger call to a method
     */

    private class ExitCheckMethodAdapter extends RuleCheckMethodAdapter
    {
        private int access;
        private String name;
        private String descriptor;
        private String signature;
        private String[] exceptions;
        private Vector<Label> startLabels;
        private Vector<Label> endLabels;

        ExitCheckMethodAdapter(MethodVisitor mv, Rule rule, int access, String name, String descriptor, String signature, String[] exceptions)
        {
            super(mv, rule, access, name, descriptor);
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
            this.signature = signature;
            this.exceptions = exceptions;
            startLabels = new Vector<Label>();
            endLabels = new Vector<Label>();
        }

        /**
         * Visits a try catch block and records the label of the handler start if the
         * exception type EarlyReturnException so we can later avoid inserting a rule
         * trigger.
         *
         * @param start beginning of the exception handler's scope (inclusive).
         * @param end end of the exception handler's scope (exclusive).
         * @param handler beginning of the exception handler's code.
         * @param type internal name of the type of exceptions handled by the
         *        handler, or <tt>null</tt> to catch any exceptions (for "finally"
         *        blocks).
         * @throws IllegalArgumentException if one of the labels has already been
         *         visited by this visitor (by the {@link #visitLabel visitLabel}
         *         method).
         */
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type)
        {
            // check whether type is one of ours and if so add the labels to the
            // return table

            if (type.equals("org/jboss/byteman/rule/exception/EarlyReturnException")) {
                earlyReturnHandlers.add(handler);
            }
            super.visitTryCatchBlock(start, end, handler, type);
        }

        /**
         * each time we visit a label we set or clear flag inhibit depending upon whether the label
         * identifies an EarlyReturnException block or not in order to avoid inserting triggers
         * for returns added by our own exception handling code
         *
         * @param label
         */
        public void visitLabel(Label label)
        {
            if (earlyReturnHandlers.contains(label)) {
                inhibit = true;
            } else {
                inhibit = false;
            }

            super.visitLabel(label);
        }

        /**
         * we need to identify return instructions which are inserted because of other rules
         *
         * @param opcode
         */
        public void visitInsn(final int opcode) {
            switch (opcode) {
                case Opcodes.RETURN: // empty stack
                case Opcodes.IRETURN: // 1 before n/a after
                case Opcodes.FRETURN: // 1 before n/a after
                case Opcodes.ARETURN: // 1 before n/a after
                case Opcodes.LRETURN: // 2 before n/a after
                case Opcodes.DRETURN: // 2 before n/a after
                {
                    if (!inhibit) {
                        // ok this is not one of our inserted return instructions so record this as a trigger point
                        setTriggerPoint();
                    }
                }
                break;
            }

            super.visitInsn(opcode);
        }

        public void visitEnd()
        {
            if (checkBindings()) {
                setVisitOk();
            }
            super.visitEnd();
        }

        private boolean inhibit;
    }
}