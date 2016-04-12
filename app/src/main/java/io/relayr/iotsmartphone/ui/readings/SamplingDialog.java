package io.relayr.iotsmartphone.ui.readings;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.SwitchCompat;
import android.util.AttributeSet;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.greenrobot.event.EventBus;
import io.relayr.iotsmartphone.R;
import io.relayr.iotsmartphone.storage.Constants;
import io.relayr.iotsmartphone.utils.ReadingUtils;
import io.relayr.iotsmartphone.storage.Storage;
import io.relayr.iotsmartphone.utils.UiHelper;

import static android.widget.Toast.LENGTH_LONG;
import static io.relayr.iotsmartphone.storage.Constants.DeviceType.PHONE;
import static io.relayr.iotsmartphone.storage.Constants.DeviceType.WATCH;
import static io.relayr.iotsmartphone.storage.Constants.SAMPLING_PHONE_MIN;
import static io.relayr.iotsmartphone.storage.Constants.SAMPLING_WATCH_MIN;

public class SamplingDialog extends LinearLayout {

    @InjectView(R.id.dialog_unit) TextView mUnitTv;
    @InjectView(R.id.dialog_identifier) TextView mIdentifierTv;

    @InjectView(R.id.sampling_low) TextView mSamplingLow;
    @InjectView(R.id.sampling_high) TextView mSamplingHigh;
    @InjectView(R.id.sampling_seek) SeekBar mSamplingSeek;
    @InjectView(R.id.sampling_info) TextView mSamplingInfo;

    @InjectView(R.id.cloud_local) TextView mCloudLocal;
    @InjectView(R.id.cloud_upload) SwitchCompat mSwitch;
    @InjectView(R.id.cloud_uploading) TextView mCloudUploading;

    private String mMeaning;
    private String mPath;
    private String mUnit;
    private Constants.DeviceType mType;

    public SamplingDialog(Context context) {
        this(context, null);
    }

    public SamplingDialog(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SamplingDialog(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setUp(String meaning, String path, String unit, Constants.DeviceType type) {
        this.mMeaning = meaning;
        this.mPath = path;
        this.mUnit = unit;
        this.mType = type;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ButterKnife.inject(this, this);

        setInfo();
        setSampling();
        setCloudSwitch();
    }

    private void setInfo() {
        mUnitTv.setText(mUnit);
        mIdentifierTv.setText((mPath == null ? "" : mPath.equals("/") ? mPath : mPath + "/") + mMeaning);
    }

    private void setSampling() {
        final boolean complex = ReadingUtils.isComplex(mMeaning);
        int frequency = 0;
        if (mType == PHONE) frequency = Storage.FREQS_PHONE.get(mMeaning);
        else if (mType == WATCH) frequency = Storage.FREQS_WATCH.get(mMeaning);
        setFrequency(frequency, complex);

        mSamplingLow.setText(complex ? getContext().getString(R.string.dialog_low) :
                ((mType == PHONE ? SAMPLING_PHONE_MIN : SAMPLING_WATCH_MIN) + " s"));
        mSamplingHigh.setText(complex ? getContext().getString(R.string.dialog_high) : ((Constants.SAMPLING_MAX + 1) + " s"));

        mSamplingSeek.setMax(Constants.SAMPLING_MAX);
        if (complex)
            mSamplingSeek.setProgress((frequency / Constants.SAMPLING_COMPLEX) - 1);
        else
            mSamplingSeek.setProgress(frequency - (mType == PHONE ? SAMPLING_PHONE_MIN : SAMPLING_WATCH_MIN));

        mSamplingSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int frequency, boolean fromUser) {
                final int freq = Storage.instance().saveFrequency(mMeaning, mType,
                        frequency + (mType == PHONE ? SAMPLING_PHONE_MIN : SAMPLING_WATCH_MIN));
                if (mType == WATCH)
                    EventBus.getDefault().post(new Constants.WatchSamplingUpdate(mMeaning, freq));
                setFrequency(freq, complex);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setFrequency(int freq, boolean complex) {
        if (complex) mSamplingInfo.setText(freq >= 1000 ? (freq / 1000f) + " s" : freq + " ms");
        else mSamplingInfo.setText(freq + " s");
    }

    private void setCloudSwitch() {
        mSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (UiHelper.isCloudConnected()) {
                    Storage.instance().saveActivity(mMeaning, mType, isChecked);
                    setSwitchInfo(isChecked);
                    if (mMeaning.equals("acceleration") || mMeaning.equals("angularSpeed"))
                        showAccelerometerWarning();
                } else {
                    Toast.makeText(getContext(), getContext().getResources().getString(R.string.cloud_establish_connection), LENGTH_LONG).show();
                }
            }
        });

        boolean uploading = false;
        if (mType == PHONE) uploading = Storage.ACTIVITY_PHONE.get(mMeaning);
        else if (mType == WATCH) uploading = Storage.ACTIVITY_WATCH.get(mMeaning);

        mSwitch.setChecked(uploading);
        setSwitchInfo(uploading);
    }

    private void setSwitchInfo(boolean uploading) {
        mCloudLocal.setTextColor(ContextCompat.getColor(getContext(), uploading ? R.color.text_color : R.color.accent));
        mCloudUploading.setTextColor(ContextCompat.getColor(getContext(), uploading ? R.color.accent : R.color.text_color));
    }

    private void showAccelerometerWarning() {
        if (Storage.instance().isWarningShown())
            Toast.makeText(getContext(), getContext().getResources().getString(R.string.sv_warning_toast), LENGTH_LONG).show();
        else
            new AlertDialog.Builder(getContext(), R.style.AppTheme_DialogOverlay)
                    .setTitle(getContext().getResources().getString(R.string.sv_warning_dialog_title))
                    .setIcon(R.drawable.ic_warning)
                    .setMessage(getContext().getResources().getString(R.string.sv_warning_dialog_text))
                    .setPositiveButton(getContext().getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int i) {
                            Storage.instance().warningShown();
                            dialog.dismiss();
                        }
                    }).show();
    }
}