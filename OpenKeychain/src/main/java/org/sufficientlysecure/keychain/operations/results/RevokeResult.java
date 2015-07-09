package org.sufficientlysecure.keychain.operations.results;

import android.app.Activity;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.service.input.CryptoInputParcel;
import org.sufficientlysecure.keychain.service.input.RequiredInputParcel;
import org.sufficientlysecure.keychain.ui.LogDisplayActivity;
import org.sufficientlysecure.keychain.ui.LogDisplayFragment;
import org.sufficientlysecure.keychain.ui.util.Notify;

public class RevokeResult extends InputPendingResult {

    public final long mMasterKeyId;

    public RevokeResult(int result, OperationLog log, long masterKeyId) {
        super(result, log);
        mMasterKeyId = masterKeyId;
    }

    /**
     * used when more input is required
     * @param log operation log upto point of required input, if any
     * @param requiredInput represents input required
     */
    public RevokeResult(@Nullable OperationLog log, RequiredInputParcel requiredInput,
                        CryptoInputParcel cryptoInputParcel) {
        super(log, requiredInput, cryptoInputParcel);
        // we won't use these values
        mMasterKeyId = -1;
    }

    /** Construct from a parcel - trivial because we have no extra data. */
    public RevokeResult(Parcel source) {
        super(source);
        mMasterKeyId = source.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(mMasterKeyId);
    }

    public static final Parcelable.Creator<RevokeResult> CREATOR = new Parcelable.Creator<RevokeResult>() {
        @Override
        public RevokeResult createFromParcel(Parcel in) {
            return new RevokeResult(in);
        }

        @Override
        public RevokeResult[] newArray(int size) {
            return new RevokeResult[size];
        }
    };

    @Override
    public Notify.Showable createNotify(final Activity activity) {

        int resultType = getResult();

        String str;
        int duration;
        Notify.Style style;

        // Not an overall failure
        if ((resultType & OperationResult.RESULT_ERROR) == 0) {

            duration = Notify.LENGTH_LONG;

            // New and updated keys
            if (resultType == OperationResult.RESULT_OK) {
                style = Notify.Style.OK;
                str = activity.getString(R.string.revoke_ok);
            } else {
                duration = 0;
                style = Notify.Style.ERROR;
                str = "internal error";
            }

        } else {
            duration = 0;
            style = Notify.Style.ERROR;
            str = activity.getString(R.string.revoke_fail);
        }

        return Notify.create(activity, str, duration, style, new Notify.ActionListener() {
            @Override
            public void onAction() {
                Intent intent = new Intent(
                        activity, LogDisplayActivity.class);
                intent.putExtra(LogDisplayFragment.EXTRA_RESULT, RevokeResult.this);
                activity.startActivity(intent);
            }
        }, R.string.snackbar_details);

    }
}