package com.olm.crimemap;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Tyson Macdonald
 *
 * Class for animation the width of a view, opening and closing
 * With commands to extend and retract the animation.
 */

public class LayoutWidthAnimator {

    private final static int ANIMATION_TIME = 500;

    final ValueAnimator anim;


    public LayoutWidthAnimator(final View layout, final int width_limit){

        anim = ValueAnimator.ofInt(0, width_limit);

        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {

                if(layout.getVisibility() != View.VISIBLE) {
                    layout.setVisibility(View.VISIBLE);  //only works with an endpoint of zero
                }

                int val = (Integer) valueAnimator.getAnimatedValue();
                ViewGroup.LayoutParams layoutParams = layout.getLayoutParams();
                layoutParams.width = (val > width_limit ? width_limit : val);
                layout.setLayoutParams(layoutParams);

                // if after the frame change, the height is zero, set gone
                if (layoutParams.width == 0){
                    layout.setVisibility(View.GONE);
                }

            }
        });
        anim.setDuration(ANIMATION_TIME); //set the animation time

    }


    public void extend(){
        anim.start();
    }

    public void retract(){
        anim.reverse();
    }
}
