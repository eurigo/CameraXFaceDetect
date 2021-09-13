package com.eurigo.cameraxfacedetect;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.media.FaceDetector;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.internal.utils.ImageUtil;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.ColorUtils;
import com.blankj.utilcode.util.ImageUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.PathUtils;
import com.blankj.utilcode.util.PermissionUtils;
import com.blankj.utilcode.util.ThreadUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * @author Eurigo
 * Created on 2021/6/28 11:03
 * desc   :
 */
public class CameraxDetectActivity extends AppCompatActivity {

    private static final int MAX_FIND_COUNT = 3;
    private static final String FACE_AVATAR_PATH = PathUtils.getExternalDownloadsPath() + "/face.png";
    private static final long COLOR_CHANGE_TIME = 200;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private FaceBorderView facesView;
    private FaceDetector.Face[] faces;
    private int findCount;
    private TextView tvTip;
    private List<Integer> colorList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect);
        previewView = findViewById(R.id.previewView);
        facesView = findViewById(R.id.face_bound);
        initPermission();
        faces = new FaceDetector.Face[MAX_FIND_COUNT];
        tvTip = findViewById(R.id.tv_tip);
        initColorList();
        countDownTimer.start();
    }

    private void initColorList() {
        colorList = new ArrayList<>(6);
        colorList.add(R.color.white);
        colorList.add(R.color.white_90);
        colorList.add(R.color.white_80);
        colorList.add(R.color.white_60);
        colorList.add(R.color.white_40);
        colorList.add(R.color.white_20);
    }

    private final CountDownTimer countDownTimer
            = new CountDownTimer(6 * COLOR_CHANGE_TIME, COLOR_CHANGE_TIME) {
        @Override
        public void onTick(long millisUntilFinished) {
            if (colorList.size() > 0) {
                tvTip.setTextColor(ColorUtils.getColor(colorList.get(0)));
                colorList.remove(0);
            }
        }

        @Override
        public void onFinish() {
            initColorList();
            countDownTimer.start();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        facesView.removeRect();
    }

    @Override
    protected void onPause() {
        facesView.removeRect();
        super.onPause();
    }

    private void initPermission() {
        PermissionUtils.permission(PermissionConstants.CAMERA, PermissionConstants.STORAGE)
                .callback(new PermissionUtils.SimpleCallback() {
                    @Override
                    public void onGranted() {
                        startCamera();
                    }

                    @Override
                    public void onDenied() {
                        ToastUtils.showShort("没有相机权限");
                    }
                })
                .request();
    }

    private void startCamera() {
        ToastUtils.showShort("启动相机");
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindImageAnalysis(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        // 选择前摄相机
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();
        // 创建预览
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // 创建图像分析用例
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        // 设置分析用例
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this)
                , new ImageAnalysis.Analyzer() {
                    @Override
                    public void analyze(@NonNull ImageProxy imageProxy) {
                        new FaceDetectTask(imageProxy).execute();
                    }
                });

        // 绑定生命周期
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
    }

    @SuppressLint("RestrictedApi")
    public void detectFace(ImageProxy imageProxy) {
        try {
            // 提取人脸二进制数据，并转换成RGB_565数据
            Bitmap rgbBitmap = ImageUtils.getBitmap(Objects.requireNonNull(ImageUtil.imageToJpegByteArray(imageProxy)), 0)
                    .copy(Bitmap.Config.RGB_565, true);
            // 获取数据旋转角
            int rotate = imageProxy.getImageInfo().getRotationDegrees();
            // 生成真正的人脸识别位图
            Bitmap finalBitmap = ImageUtils.rotate(rgbBitmap, rotate, 0, 0);
            float scaleX = (float) previewView.getWidth() / finalBitmap.getWidth();
            float scaleY = (float) previewView.getHeight() / finalBitmap.getHeight();
            FaceDetector faceDetector = new FaceDetector(finalBitmap.getWidth(), finalBitmap.getHeight(), MAX_FIND_COUNT);
            findCount = faceDetector.findFaces(finalBitmap, faces);
            if (findCount > 0) {
                LogUtils.e("检测到人脸", "识别到的人脸数：" + findCount);
                drawRectAndSaveImg(finalBitmap, scaleX, scaleY);
            } else {
                facesView.removeRect();
                LogUtils.e("未检测到人脸");
            }
        } catch (ImageUtil.CodecFailedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取需要上传的人脸图片
     *
     * @param face   保存的人脸
     * @param bitmap 检测的图
     */
    private void saveImage(FaceDetector.Face face, Bitmap bitmap) {
        PointF bounds = new PointF();
        face.getMidPoint(bounds);
        int space = getAccurateInt(face.eyesDistance());
        if (space < 40) {
            return;
        }
        int x = getAccurateInt(bounds.x);
        int y = getAccurateInt(bounds.y);

        // 默认的截取宽高
        int defaultWidth = Math.min(3 * space, bitmap.getWidth());
        int defaultHeight = Math.min(4 * space, bitmap.getHeight());
        int x3 = 3 * space;
        int x11divide3 = 11 * space / 3;
        // X轴开始的截取位置
        int clipX = Math.max(x - x3, 0);
        // Y轴开始的截取位置
        int clipY = Math.max(y - x11divide3, 0);
        // 转换X轴截取长度
        int clipWidth = clipX + defaultWidth > bitmap.getWidth()
                ? bitmap.getWidth() - clipX : defaultWidth;
        // 转换Y轴截取长度
        int clipHeight = clipY + defaultHeight > bitmap.getHeight()
                ? bitmap.getHeight() - clipY : defaultHeight;

        if (!bitmap.isRecycled()) {
            Bitmap saveBitmap = ImageUtils.clip(bitmap, clipX, clipY
                    , clipWidth, clipHeight, true);
            ImageUtils.save(saveBitmap, FACE_AVATAR_PATH
                    , Bitmap.CompressFormat.PNG, true);
        }
    }

    /**
     * 获取更精准的Int强转值
     */
    private int getAccurateInt(float number) {
        return (int) (number + 0.5f);
    }

    /**
     * 绘制人脸，并保存图片
     */
    private void drawRectAndSaveImg(Bitmap detectBitmap, float scaleX, float scaleY) {
        for (int i = 0; i < findCount; i++) {
            facesView.updateFaces(faces, scaleX, scaleY, findCount);
        }
        saveImage(faces[0], detectBitmap);
    }

    /**
     * 人脸检测Task
     */
    private class FaceDetectTask extends ThreadUtils.SimpleTask<Void> {

        private final ImageProxy imageProxy;

        public FaceDetectTask(ImageProxy imageProxy) {
            this.imageProxy = imageProxy;
        }

        @Override
        public Void doInBackground() throws Throwable {
            detectFace(imageProxy);
            imageProxy.close();
            return null;
        }

        @Override
        public void onSuccess(Void result) {

        }

        public void execute() {
            ThreadUtils.executeByCpu(this);
        }
    }
}
