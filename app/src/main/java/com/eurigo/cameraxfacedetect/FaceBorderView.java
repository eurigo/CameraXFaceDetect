package com.eurigo.cameraxfacedetect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.media.FaceDetector;
import android.util.AttributeSet;
import android.view.View;

import com.blankj.utilcode.util.LogUtils;

import java.util.ArrayList;
import java.util.List;


/**
 * @author Eurigo
 * Created on 2021/6/28 9:59
 * desc   : 人脸框
 */
public class FaceBorderView extends View {
    private Paint paint;
    private boolean isClear;
    private FaceDetector.Face[] faces = new FaceDetector.Face[]{};
    private float scaleX, scaleY;
    private int findCount;

    public FaceBorderView(Context context) {
        this(context, null);
    }

    public FaceBorderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FaceBorderView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        // 防止不支持硬件加速的设备，绘制后黑屏
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faces.length < 1){
            return;
        }
        // 绘制人脸框
        drawFaceRect(canvas, getRectList(), false, true);
        if (isClear) {
            canvas.drawColor(Color.WHITE, PorterDuff.Mode.CLEAR);
            isClear = false;
        }
    }

    /**
     * 绘制人脸框
     *
     * @param canvas        canvas
     * @param rectList      所有人脸框矩形
     * @param isDrawRect    是否全边框
     * @param isFrontCamera 是否是前置摄像头，前置是镜像成像，需要镜像处理
     */
    public void drawFaceRect(Canvas canvas, List<RectF> rectList , boolean isDrawRect, boolean isFrontCamera) {
        if (canvas == null) {
            LogUtils.e("canvas == null");
            return;
        }

        for (RectF rectF: rectList) {
            // 小边长度为矩形高度的1/4
            float len = (rectF.bottom - rectF.top) / 4f;
            // 设置人脸框
            if (len / 12 >= 2) {
                paint.setStrokeWidth(len / 12);
            } else {
                paint.setStrokeWidth(2);
            }

            // 是否绘制矩形框
            if (isDrawRect) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawRect(rectF, paint);
            } else {
                // 前摄成像是镜像，绘制前需要对调
                if (isFrontCamera) {
                    float temp = rectF.left;
                    rectF.left = canvas.getWidth() - rectF.right;
                    rectF.right = canvas.getWidth() - temp;
                }
                float drawLeft = rectF.left - len;
                float drawTop = rectF.top - len;
                float drawRight = rectF.right + len;
                float drawBottom = rectF.bottom + len;
                // 线长补足
                float lineWidth = paint.getStrokeWidth() / 2;
                // 左下，竖线
                canvas.drawLine(drawLeft, drawBottom, drawLeft, drawBottom - len, paint);
                // 左下，横线
                canvas.drawLine(drawLeft - lineWidth, drawBottom, drawLeft + len, drawBottom, paint);
                // 右下，竖线
                canvas.drawLine(drawRight, drawBottom, drawRight, drawBottom - len, paint);
                // 右下，横线
                canvas.drawLine(drawRight + lineWidth, drawBottom, drawRight - len, drawBottom, paint);
                // 左上，竖线
                canvas.drawLine(drawLeft, drawTop, drawLeft, drawTop + len, paint);
                // 左上，横线
                canvas.drawLine(drawLeft - lineWidth, drawTop, drawLeft + len, drawTop, paint);
                // 右上，竖线
                canvas.drawLine(drawRight, drawTop, drawRight, drawTop + len, paint);
                // 右上，横线
                canvas.drawLine(drawRight + lineWidth, drawTop, drawRight - len, drawTop, paint);
            }
        }

    }

    /**
     * 绘制脸部方框
     */
    public void updateFaces(FaceDetector.Face[] faces, float scaleX, float scaleY, int findCount) {
        this.faces = faces;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.findCount = findCount;
        postInvalidate();
    }

    /**
     * 清除已经画上去的框
     */
    public void removeRect() {
        isClear = true;
        postInvalidate();
    }

    /**
     * 获取绘制的所有人脸框
     * @return 所有绘制的矩形
     */
    private List<RectF> getRectList() {
        List<RectF> list = new ArrayList<>();
        for (int i = 0; i < findCount; i++) {
            PointF bounds = new PointF();
            faces[i].getMidPoint(bounds);
            float spec = faces[i].eyesDistance();
            float x = bounds.x;
            float y = bounds.y;
            float left = (x - spec) * scaleX;
            float top = (y - spec) * scaleY;
            float right = (x + spec) * scaleX;
            float bottom = (y + spec) * scaleY;
            list.add(new RectF(left, top, right, bottom));
        }
        return list;
    }
}
