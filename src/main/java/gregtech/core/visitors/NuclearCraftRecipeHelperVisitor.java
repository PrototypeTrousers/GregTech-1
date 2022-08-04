package gregtech.core.visitors;

import gregtech.core.GregTechTransformer;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class NuclearCraftRecipeHelperVisitor extends ClassVisitor implements Opcodes {
    public static final String TARGET_CLASS_NAME = "nc/integration/gtce/GTCERecipeHelper";

    public NuclearCraftRecipeHelperVisitor(ClassVisitor cv) {
        super(ASM5, cv);
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
        if (name.equals("isRecipeConflict")) {
            return new RecipeConflictVisitor(visitor);
        } else if (name.equals("addGTCERecipe")) {
            return new NuclearCraftaddRecipeVisitor(visitor);
        }
        return visitor;
    }

    static class RecipeConflictVisitor extends MethodVisitor implements Opcodes {
        private int fluidStackCasts;

        public RecipeConflictVisitor(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            GregTechTransformer.is_currently_computing_frames = true;
            super.visitMaxs(maxStack, maxLocals);
            GregTechTransformer.is_currently_computing_frames = false;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (opcode == INVOKEVIRTUAL) {
                if (owner.equals("gregtech/api/recipes/CountableIngredient") && name.equals("getIngredient")) {
                    return;
                } else if (owner.equals("net/minecraft/item/crafting/Ingredient") && name.equals("apply")) {
                    owner = "gregtech/api/recipes/ingredients/GTRecipeInput";
                    name = "acceptsStack";
                    desc = "(Lnet/minecraft/item/ItemStack;)Z";
                } else if (owner.equals("net/minecraftforge/fluids/FluidStack") && name.equals("isFluidEqual")) {
                    owner = "gregtech/api/recipes/ingredients/GTRecipeInput";
                    name = "acceptsFluid";
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            if (type == F_APPEND) {
                if (fluidStackCasts == 1 && nLocal == 2 && local[0] instanceof String && local[0].equals("net/minecraftforge/fluids/FluidStack")) {
                    local[0] = "gregtech/api/recipes/ingredients/GTRecipeInput";
                }
            }
            super.visitFrame(type, nLocal, local, nStack, stack);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == CHECKCAST && type.equals("gregtech/api/recipes/CountableIngredient")) {
                type = "gregtech/api/recipes/ingredients/GTRecipeInput";
            } else if (opcode == CHECKCAST && type.equals("net/minecraftforge/fluids/FluidStack")) {
                this.fluidStackCasts++;
                if (this.fluidStackCasts == 2) {
                    type = "gregtech/api/recipes/ingredients/GTRecipeInput";
                }
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (fluidStackCasts == 2 && opcode == ALOAD) {
                if (var == 6) {
                    var = 4;
                } else if (var == 4) {
                    var = 6;
                }
            }
            super.visitVarInsn(opcode, var);
        }
    }

    static class NuclearCraftaddRecipeVisitor extends MethodVisitor implements Opcodes {
        private int circuits;
        private int dup;
        private int newArray;
        private int iConst0;
        private int iConst1;
        private int iAstore;

        public NuclearCraftaddRecipeVisitor(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            GregTechTransformer.is_currently_computing_frames = true;
            super.visitMaxs(maxStack, maxLocals);
            GregTechTransformer.is_currently_computing_frames = false;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (opcode == INVOKESPECIAL && owner.equals("gregtech/api/recipes/ingredients/IntCircuitIngredient") && name.equals("<init>")) {
                desc = "(I)V";
            }
            if (opcode == INVOKEVIRTUAL && owner.equals("gregtech/api/recipes/RecipeBuilder") && name.equals("notConsumable") && desc.equals("(Lnet/minecraft/item/crafting/Ingredient;)Lgregtech/api/recipes/RecipeBuilder;")) {
                desc = "(Lgregtech/api/recipes/ingredients/GTRecipeInput;)Lgregtech/api/recipes/RecipeBuilder;";
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == NEW && type.equals("gregtech/api/recipes/ingredients/IntCircuitIngredient")) {
                circuits++;
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitInsn(int opcode) {
            if (circuits > 0 && circuits < 5) {
                if (opcode == DUP && dup < 8) {
                    dup++;
                    if (dup == 2 || dup == 4 || dup == 6 || dup == 8) {
                        return;
                    }
                } else if (opcode == ICONST_0 && iConst0 < 7) {
                    iConst0++;
                    if (iConst0 == 1 || iConst0 == 3 || iConst0 == 5 || iConst0 == 7) {
                        return;
                    }
                    if (iConst0 == 2) {
                        opcode = ICONST_1;
                    }
                } else if (opcode == ICONST_1 && iConst1 < 5) {
                    iConst1++;
                    if (iConst1 == 1 || iConst1 == 2 || iConst1 == 3 || iConst1 == 5) {
                        return;
                    }
                } else if (opcode == IASTORE && iAstore < 4) {
                    iAstore++;
                    return;
                }
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == NEWARRAY) {
                if (circuits > 0 && circuits < 5) {
                    if (newArray < 4) {
                        newArray++;
                        return;
                    }
                }
            }
            super.visitIntInsn(opcode, operand);
        }

        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == GETSTATIC && name.equals("FLUID_EXTRACTION_RECIPES")) { // FLUID_EXTRACTION_RECIPES -> EXTRACTOR_RECIPES
                name = "EXTRACTOR_RECIPES";
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }
    }
}


