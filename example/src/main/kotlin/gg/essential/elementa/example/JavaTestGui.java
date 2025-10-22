package gg.essential.elementa.example;

import gg.essential.elementa.ElementaVersion;
import gg.essential.elementa.UIComponent;
import gg.essential.elementa.WindowScreen;
import gg.essential.elementa.components.UIBlock;
import gg.essential.elementa.constraints.CenterConstraint;
import gg.essential.elementa.constraints.ChildBasedSizeConstraint;
import gg.essential.elementa.constraints.PixelConstraint;
import gg.essential.elementa.constraints.animation.AnimatingConstraints;
import gg.essential.elementa.constraints.animation.Animations;
import gg.essential.elementa.effects.ScissorEffect;

public class JavaTestGui extends WindowScreen {
    UIComponent box = new UIBlock()
        .setX(new CenterConstraint())
        .setY(new PixelConstraint(10f))
        .setWidth(new PixelConstraint(10f))
        .setHeight(new PixelConstraint(36f))
        .setChildOf(getWindow())
        .enableEffect(new ScissorEffect());

    public JavaTestGui() {
        super(ElementaVersion.V2);
        box.onMouseEnterRunnable(() -> {
            // Animate, set color, etc.
            AnimatingConstraints anim = box.makeAnimation();
            anim.setWidthAnimation(Animations.OUT_EXP, 0.5f, new ChildBasedSizeConstraint(2f));
            anim.onCompleteRunnable(() -> {
                // Trigger new animation or anything.
            });
            box.animateTo(anim);
        });
    }
}
