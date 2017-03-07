package org.sufficientlysecure.keychain.service;


import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;

import com.cryptolib.SecureDataSocket;
import com.cryptolib.SecureDataSocketException;

import org.sufficientlysecure.keychain.util.FileHelper;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class PrivateKeyImportExportService extends IntentService {
    public static final String EXTRA_EXPORT_KEY = "export_key";
    public static final String EXTRA_IMPORT_CONNECTION_DETAILS = "import_connection_details";
    public static final String EXTRA_IMPORT_IP_ADDRESS = "import_ip_address";
    public static final String EXTRA_IMPORT_PORT = "import_port";

    public static final String ACTION_STOP = "action_stop";

    public static final String EXPORT_ACTION_UPDATE_CONNECTION_DETAILS = "export_update_connection_details";
    public static final String EXPORT_ACTION_SHOW_PHRASE = "export_show_phrase";
    public static final String EXPORT_ACTION_KEY = "export_key";
    public static final String EXPORT_ACTION_FINISHED = "export_finished";
    public static final String EXPORT_EXTRA = "export_extra";

    public static final String EXPORT_ACTION_MANUAL_MODE = "export_manual_mode";
    public static final String EXPORT_ACTION_PHRASES_MATCHED = "export_phrases_matched";
    public static final String EXPORT_ACTION_CACHED_URI = "export_cached_uri";

    public static final String IMPORT_ACTION_KEY = "import_key";
    public static final String IMPORT_ACTION_SHOW_PHRASE = "import_show_phrase";
    public static final String IMPORT_EXTRA = "import_extra";

    public static final String IMPORT_ACTION_PHRASES_MATCHED = "import_phrases_matched";

    private static final int PORT = 5891;

    private SecureDataSocket mSecureDataSocket;
    private Semaphore mLock;
    private String mConnectionDetails = null;
    private boolean mPhrasesMatched;

    private LocalBroadcastManager mBroadcaster;
    private Uri mCachedUri;

    private boolean mStopped;

    public PrivateKeyImportExportService() {
        super("PrivateKeyImportExportService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBroadcaster = LocalBroadcastManager.getInstance(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        System.out.println("---> Service started...");

        boolean export = intent.getBooleanExtra(EXTRA_EXPORT_KEY, false);
        String connectionDetails = intent.getStringExtra(EXTRA_IMPORT_CONNECTION_DETAILS);
        String ipAddress = intent.getStringExtra(EXTRA_IMPORT_IP_ADDRESS);
        int port = intent.getIntExtra(EXTRA_IMPORT_PORT, 0);

        IntentFilter filter = new IntentFilter();
        filter.addAction(PrivateKeyImportExportService.EXPORT_ACTION_MANUAL_MODE);
        filter.addAction(PrivateKeyImportExportService.EXPORT_ACTION_PHRASES_MATCHED);
        filter.addAction(PrivateKeyImportExportService.EXPORT_ACTION_CACHED_URI);
        filter.addAction(PrivateKeyImportExportService.IMPORT_ACTION_PHRASES_MATCHED);
        filter.addAction(PrivateKeyImportExportService.ACTION_STOP);

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);

        mLock = new Semaphore(0);

        if (export) {
            exportKey();
        } else if (connectionDetails != null){
            importKey(connectionDetails);
        } else if (ipAddress != null && port > 0) {
            importKey(ipAddress, port);
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);

        System.out.println("---> Service stopped :)");
    }

    private void exportKey() {
        try {
            mSecureDataSocket = new SecureDataSocket(PORT);
            mConnectionDetails = mSecureDataSocket.prepareServerWithClientCamera();
        } catch (SecureDataSocketException e) {
            e.printStackTrace();
        }

        broadcastExport(EXPORT_ACTION_UPDATE_CONNECTION_DETAILS, mConnectionDetails);

        boolean exportedViaQrCode = false;
        try {
            mSecureDataSocket.setupServerWithClientCamera();

            exportedViaQrCode = true;
            broadcastExport(EXPORT_ACTION_KEY, null);
        } catch (SecureDataSocketException e) {
            // this exception is thrown when socket is closed (user switches from QR code to manual ip input)
            mSecureDataSocket.close();
            exportWithoutQrCode();
        }

        if (!mStopped && (exportedViaQrCode || mPhrasesMatched)) {
            lock();
            if (mStopped) {
                return;
            }

            try {
                byte[] exportData = FileHelper.readBytesFromUri(this, mCachedUri);
                mSecureDataSocket.write(exportData);
            } catch (SecureDataSocketException | IOException e) {
                e.printStackTrace();
            }
        }

        broadcastExport(EXPORT_ACTION_FINISHED, null);
    }

    private void exportWithoutQrCode() {
        String phrase = null;
        try {
            phrase = mSecureDataSocket.setupServerNoClientCamera();
        } catch (SecureDataSocketException e) {
            e.printStackTrace();
        }

        broadcastExport(EXPORT_ACTION_SHOW_PHRASE, phrase);

        lock();
        if (mStopped) {
            return;
        }

        try {
            mSecureDataSocket.comparedPhrases(mPhrasesMatched);
        } catch (SecureDataSocketException e) {
            e.printStackTrace();
        }

        if (mPhrasesMatched) {
            broadcastExport(EXPORT_ACTION_KEY, null);
        }
    }

    private void importKey(String connectionDetails) {
        mSecureDataSocket = new SecureDataSocket(PORT);
        try {
            mSecureDataSocket.setupClientWithCamera(connectionDetails);
        } catch (SecureDataSocketException e) {
            e.printStackTrace();
        }

        importKey();
    }

    private void importKey(String ipAddress, int port) {
        mSecureDataSocket = new SecureDataSocket(port);

        String connectionDetails = ipAddress + ":" + port;
        String comparePhrase = null;
        try {
            comparePhrase = mSecureDataSocket.setupClientNoCamera(connectionDetails);
        } catch (SecureDataSocketException e) {
            e.printStackTrace();
        }

        Intent intent = new Intent(IMPORT_ACTION_SHOW_PHRASE);
        intent.putExtra(IMPORT_EXTRA, comparePhrase);
        mBroadcaster.sendBroadcast(intent);

        lock();
        if (mStopped) {
            return;
        }

        try {
            mSecureDataSocket.comparedPhrases(mPhrasesMatched);
        } catch (SecureDataSocketException e) {
            e.printStackTrace();
        }

        if (mPhrasesMatched) {
            importKey();
        }
    }

    private void importKey() {
        byte[] keyRing = null;
        try {
            keyRing = mSecureDataSocket.read();
        } catch (SecureDataSocketException e) {
            e.printStackTrace();
        }
        mSecureDataSocket.close();

        Intent intent = new Intent(IMPORT_ACTION_KEY);
        intent.putExtra(IMPORT_EXTRA, keyRing);
        mBroadcaster.sendBroadcast(intent);
    }

    private void lock() {
        boolean interrupted;
        do {
            try {
                mLock.acquire();
                interrupted = false;
            } catch (InterruptedException e) {
                interrupted = true;
                e.printStackTrace();
            }
        } while (interrupted);
    }

    private void broadcastExport(String action, String extra) {
        Intent intent = new Intent(action);
        intent.putExtra(EXPORT_EXTRA, extra);

        mBroadcaster.sendBroadcast(intent);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
                case EXPORT_ACTION_MANUAL_MODE:
                    if (mSecureDataSocket != null) {
                        mSecureDataSocket.close();
                    }
                    break;
                case EXPORT_ACTION_PHRASES_MATCHED:
                    mPhrasesMatched = intent.getBooleanExtra(EXPORT_EXTRA, false);
                    mLock.release();
                    break;
                case EXPORT_ACTION_CACHED_URI:
                    mCachedUri = intent.getParcelableExtra(EXPORT_EXTRA);
                    mLock.release();
                    break;
                case IMPORT_ACTION_PHRASES_MATCHED:
                    mPhrasesMatched = intent.getBooleanExtra(IMPORT_EXTRA, false);
                    mLock.release();
                    break;
                case ACTION_STOP:
                    mStopped = true;
                    mSecureDataSocket.close();
                    mLock.release();
                    break;
            }
        }
    };
}
