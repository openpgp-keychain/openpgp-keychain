package org.sufficientlysecure.keychain.ui.wizard;


import android.content.Context;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.Bundle;

import org.sufficientlysecure.keychain.ui.base.BaseViewModel;
import org.sufficientlysecure.keychain.util.Passphrase;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class NFCUnlockWizardFragmentViewModel implements BaseViewModel {
    public static final String STATE_SAVE_OPERATION_STATE = "STATE_SAVE_OPERATION_STATE";
    public static final String STATE_SAVE_NFC_PIN = "STATE_SAVE_NFC_PIN";
    private Context mContext;
    private OnViewModelEventBind mOnViewModelEventBind;
    private OperationState mOperationState;
    private Passphrase mNfcPin;

    /**
     * Operation state
     */
    public enum OperationState {
        OPERATION_STATE_WAITING_FOR_NFC_TAG,
        OPERATION_STATE_NFC_PIN_UPLOAD,
        OPERATION_STATE_CARD_READY
    }

    /**
     * View Model communication
     */
    public interface OnViewModelEventBind {
        void onOperationStateError(String error);

        void onOperationStateOK(String showText);

        void onOperationStateCompleted(String showText);

        void onTipTextUpdate(CharSequence text);

        void onShowProgressBar(boolean show);

        void onUpdateProgress(int progress);
    }

    public NFCUnlockWizardFragmentViewModel(OnViewModelEventBind onViewModelEventBind) {
        mOnViewModelEventBind = onViewModelEventBind;

        if (mOnViewModelEventBind == null) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void prepareViewModel(Bundle savedInstanceState, Bundle arguments, Context context) {
        mContext = context;
        if (savedInstanceState != null) {
            restoreViewModelState(savedInstanceState);
        } else {
            initializeUnlockOperation();
        }
    }

    @Override
    public void saveViewModelState(Bundle outState) {
        outState.putSerializable(STATE_SAVE_OPERATION_STATE, mOperationState);
        outState.putParcelable(STATE_SAVE_NFC_PIN, mNfcPin);
    }

    @Override
    public void restoreViewModelState(Bundle savedInstanceState) {
        mNfcPin = savedInstanceState.getParcelable(STATE_SAVE_NFC_PIN);
        mOperationState = (OperationState) savedInstanceState.
                getSerializable(STATE_SAVE_OPERATION_STATE);
    }

    /**
     * Initializes the operation
     */
    public void initializeUnlockOperation() {
        mOperationState = OperationState.OPERATION_STATE_WAITING_FOR_NFC_TAG;
    }

    /**
     * Updates the operation state.
     *
     * @return
     */
    public boolean updateOperationState() {
        switch (mOperationState) {
            case OPERATION_STATE_WAITING_FOR_NFC_TAG:


                break;
            case OPERATION_STATE_NFC_PIN_UPLOAD:
                break;
            case OPERATION_STATE_CARD_READY:
                return true;
            default:
                return false;
        }

        return false;
    }

    /**
     * Reads the tag data from the nfc tag
     *
     * @param tag
     */
    public void onNfcTagDiscovery(Tag tag) {

    }

    /**
     * Generates a random 128 bit key.
     *
     * @throws NoSuchAlgorithmException
     */
    public void generateSecureRoomPin() throws NoSuchAlgorithmException {
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        byte buffer[] = new byte[16];
        sr.nextBytes(buffer);
        mNfcPin = new Passphrase(Arrays.toString(buffer));
    }


    public static class NfcATechnology {
        protected NfcA mNfcA;

        public NfcATechnology() {

        }

    }
}