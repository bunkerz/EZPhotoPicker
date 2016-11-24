package siclo.com.ezphotopicker.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import siclo.com.ezphotopicker.api.models.EZPhotoPickConfig;
import siclo.com.ezphotopicker.api.models.PhotoSource;
import siclo.com.ezphotopicker.models.PhotoIntentConstants;
import siclo.com.ezphotopicker.models.PhotoIntentException;
import siclo.com.ezphotopicker.storage.PhotoIntentHelperStorage;
import siclo.com.ezphotopicker.storage.PhotoGenerator;
import siclo.com.ezphotopicker.storage.PhotoIntentContentProvider;

/**
 * Created by ericta on 11/13/16.
 */

class PhotoIntentHelperPresenter implements PhotoIntentHelperContract.Presenter {

    /*
    Only 1 camera/gallery screen at 1 time
    it 's ok to keep static value here(for simple)
     */
    private static boolean isStoringPhoto = false;
    private static boolean isOpenedPhotoPick = false;
    private static Uri photoUri;

    private static final int STORE_SUCCESS_MSG = 0;
    private static final int STORE_FAIL_MSG = 1;

    private PhotoIntentHelperContract.View view;
    private PhotoIntentHelperStorage photoIntentHelperStorage;
    private PhotoGenerator photoGenerator;
    private EZPhotoPickConfig EZPhotoPickConfig;
    private String storingPhotoName;

    PhotoIntentHelperPresenter(PhotoIntentHelperContract.View view, PhotoGenerator photoGenerator, PhotoIntentHelperStorage photoIntentHelperStorage, EZPhotoPickConfig EZPhotoPickConfig) {
        this.view = view;
        this.photoGenerator = photoGenerator;
        this.photoIntentHelperStorage = photoIntentHelperStorage;
        this.EZPhotoPickConfig = EZPhotoPickConfig;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) throws PhotoIntentException {
        if (EZPhotoPickConfig == null) {
            throw PhotoIntentException.getNullPhotoPickConfigException();
        }

        if(isStoringPhoto){
            view.showLoading();
            return;
        }

        if(isOpenedPhotoPick){
            return;
        }

        if(EZPhotoPickConfig.photoSource == PhotoSource.CAMERA){
            view.requestCameraPermission();
        }else{
            onPickPhotoWithGalery();
        }
    }

    @Override
    public void onPhotoPickedFromGallery(final Intent data) {
        photoUri = data.getData();
        boolean isPhotoUriPointToExternalStorage = isCurrentPhotoUriPointToExternalStorage();
        if(isPhotoUriPointToExternalStorage){
            view.requestReadExternalStoragePermission();
            return;
        }
        onPhotoPicked();
    }

    @Override
    public void onRequestReadExternalPermissionGranted() {
        onPhotoPicked();
    }

    private void onPhotoPicked() {
        isOpenedPhotoPick =false;
        isStoringPhoto = true;
        view.showLoading();
        processPickedUriInBackground(photoUri);
    }

    private boolean isCurrentPhotoUriPointToExternalStorage() {
        return photoUri.toString().contains(Environment.getExternalStorageDirectory().getAbsolutePath());
    }

    private void processPickedUriInBackground(final Uri photoUri) {
        new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Bitmap pickingPhoto = photoGenerator.generatePhotoWithValue(photoUri, EZPhotoPickConfig.maxExportingSize);
                        generateStoringPhotoName();
                        photoIntentHelperStorage.storeLatestStoredPhotoName(storingPhotoName);
                        photoIntentHelperStorage.storeLatestStoredPhotoDir(EZPhotoPickConfig.internalStorageDir);
                        Bitmap storedBitmap = photoIntentHelperStorage.storePhotoBitmap(photoUri, pickingPhoto, EZPhotoPickConfig.internalStorageDir, storingPhotoName);
                        if(EZPhotoPickConfig.extraAction !=null){
                            EZPhotoPickConfig.extraAction.doExtraAction(storedBitmap);
                        }
                        photoPickHandler.sendEmptyMessage(STORE_SUCCESS_MSG);
                    } catch (IOException e) {
                        e.printStackTrace();
                        photoPickHandler.sendEmptyMessage(STORE_FAIL_MSG);
                    }

                }
        }).start();
    }

    private void generateStoringPhotoName() {
        if(EZPhotoPickConfig.isGenerateUniqueName){
            String currentTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss'Z'").format(new Date());
            storingPhotoName = currentTimeStr;
            return;
        }
        storingPhotoName = PhotoIntentConstants.TEMP_STORING_PHOTO_NAME;
    }

    @Override
    public void onPhotoPickedFromCamera(File internalDir) {
//        view.notifyGalleryDataChanged(exportedPhotoUri);
        File photoFile = new File(internalDir, PhotoIntentContentProvider.TEMP_PHOTO_NAME);
        photoUri = Uri.fromFile(photoFile);
        onPhotoPicked();
    }

    @Override
    public void onRequestCameraPermissionGranted() {
        onPickPhotoWithCamera();
    }

    @Override
    public void onRequestPermissionDenied() {
        view.showToastMessagePermissionDenied(EZPhotoPickConfig.permisionDeniedErrorStringResource);
        view.finishWithNoResult();
    }

    @Override
    public void onDestroy() {
        isStoringPhoto = false;
        isOpenedPhotoPick = false;
    }



    Handler photoPickHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            int what = msg.what;
            switch (what){
                case STORE_SUCCESS_MSG:
                    view.finishPickPhotoWithSuccessResult();
                    break;
                case STORE_FAIL_MSG:
                    view.showPickPhotoFromGalleryError(EZPhotoPickConfig.unexpectedErrorStringResource);
                    view.finishWithNoResult();
                    break;
            }
            isStoringPhoto = false;
            return false;
        }
    });

    private void onPickPhotoWithCamera() {
        isOpenedPhotoPick = true;
        view.openCamera();
    }

    private void onPickPhotoWithGalery() {
        isOpenedPhotoPick = true;
        view.openGallery();
    }
}