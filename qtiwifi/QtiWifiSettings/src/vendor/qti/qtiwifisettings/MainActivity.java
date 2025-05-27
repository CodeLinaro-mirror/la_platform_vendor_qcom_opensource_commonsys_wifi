/* Copyright (c) 2021-2022 Qualcomm Innovation Center, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted (subject to the limitations in the
 * disclaimer below) provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *
 *   * Neither the name of Qualcomm Innovation Center nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE
 * GRANTED BY THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT
 * HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package vendor.qti.qtiwifisettings;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.EditText;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Build;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.util.List;

import android.net.wifi.WifiManager;
import android.net.wifi.SupplicantState;
import com.qualcomm.qti.qtiwifi.QtiWifiManager;
import com.qualcomm.qti.qtiwifi.ThermalData;
import com.qualcomm.qti.qtiwifi.CarPlayIEData;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "QtiWifiSettingsApp";
    private Intent mServiceIntent;
    //button objects
    private Button buttonCsiStop;
    private Button buttonCommand;
    private Button buttonCarPlay;
    private EditText editTextCommand;
    private EditText editTextCarPlayVendorIE;
    private EditText editTextCarPlayAssocRespElement;
    private EditText editTextCarPlayAccessNetworkType;
    private EditText editTextCarPlayEsr;
    private EditText editTextCarPlayInternet;
    private EditText editTextCarPlayVenueType;
    private EditText editTextCarPlayVenueGroup;
    private EditText editCarPlayHessid;
    private TextView textViewCommand;
    private TextView eventViewCommand;
    private WifiManager mWifiManager;
    private static QtiWifiManager mUniqueInstance = null;
    private FileOutputStream fileout;
    private OutputStreamWriter outputWriter;
    private static CarPlayIEData mCarPlayIEData = new CarPlayIEData();

    private static final String COMMAND_GET_AVAILABLE_INTERFACES = "list-interfaces";
    private static final String COMMAND_GET_THERMAL_INFO = "get-thermal-info";
    private static final String COMMAND_REGISTER_VENDOR_EVENT_CALLBACK =
                                                 "register-vendor-event-callback";
    private static final String COMMAND_UNREGISTER_VENDOR_EVENT_CALLBACK =
                                                 "unregister-vendor-event-callback";
    private static final String COMMAND_SET_TXPOWER = "set-txpower";
    private static final String COMMAND_SET_ANI = "set-ani-level";
    private static final String COMMAND_SET_CONGESTION = "set-congestion-report";
    private static final String COMMAND_START_CSI = "start-csi";
    private static final String COMMAND_RESULT_FAILED = "FAILED";
    private static final String COMMAND_RESULT_SUCCESS = "SUCCESS";
    private static final String COMMAND_RESULT_INVALID_COMMAND = "Invalid command!";
    private static final String COMMAND_RESULT_INVALID_ARGS = "Invalid args!";
    private static final String COMMAND_SET_CARPLAY = "carplay";

    private QtiWifiManager.CsiCallback mCsiCallback = new QtiWifiManager.CsiCallback() {
        @Override
        public void onCsiUpdate(byte[] info) {
            StringBuilder strBuilder = new StringBuilder();
            for(byte val : info) {
                strBuilder.append(String.format("%02x", val&0xff));
            }
            Log.d(TAG, "onCsiUpdate csi info = " + info);
            try {
                outputWriter.write(strBuilder.toString());
                Log.i(TAG, "Successfully write into the file");
            } catch (IOException e) {
                Log.e(TAG, "Error while writing into the file");
            }
        }
    };

    private QtiWifiManager.VendorEventCallback mVendorEventCallback =
        new QtiWifiManager.VendorEventCallback() {
        @Override
        public void onThermalChanged(String ifname, int level) {
            //ignore ifname as we don't care
            Log.i(TAG, "onThermalChanged, level = " + level);
            eventViewCommand.setText("Received thermal change event: ifname = "
                             + ifname + " level=" + level
                             + "\n" + eventViewCommand.getText());
        }

        @Override
        public void onCongestionChanged(String ifname, int percentage) {
            Log.i(TAG, "onCongestionChanged, ifname = " + ifname
                    + " percentage = " + percentage);
            eventViewCommand.setText("Received congestion change event: ifname = "
                             + ifname + " percentage=" + percentage
                             + "\n" + eventViewCommand.getText());
        }
    };

    private QtiWifiManager.ApplicationBinderCallback mApplicationCallback = new QtiWifiManager.ApplicationBinderCallback() {
        @Override
        public void onAvailable(QtiWifiManager qtiWifiManager) {
            Log.d(TAG, "onAvailable called");
            mUniqueInstance = qtiWifiManager;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        QtiWifiManager.initialize(this, mApplicationCallback);
        Log.i(TAG, "initialize called");

        setContentView(R.layout.content_main);

        buttonCommand = (Button) findViewById(R.id.buttonCommand);
        editTextCommand = (EditText) findViewById(R.id.editTextCommand);
        textViewCommand = (TextView) findViewById(R.id.textViewCommand);
        eventViewCommand = (TextView) findViewById(R.id.eventViewCommand);
        buttonCommand.setOnClickListener(this);
        textViewCommand.setMovementMethod(ScrollingMovementMethod.getInstance());
        eventViewCommand.setMovementMethod(ScrollingMovementMethod.getInstance());

        // carplay relative
        editTextCarPlayVendorIE = (EditText) findViewById(R.id.editTextCarPlayVendorIE);
        editTextCarPlayAssocRespElement = (EditText) findViewById(R.id.editTextCarPlayAssocRespElement);
        editTextCarPlayAccessNetworkType = (EditText) findViewById(R.id.editTextCarPlayAccessNetworkType);
        editTextCarPlayEsr = (EditText) findViewById(R.id.editTextCarPlayEsr);
        editTextCarPlayInternet = (EditText) findViewById(R.id.editTextCarPlayInternet);
        editTextCarPlayVenueType = (EditText) findViewById(R.id.editTextCarPlayVenueType);
        editTextCarPlayVenueGroup = (EditText) findViewById(R.id.editTextCarPlayVenueGroup);
        editCarPlayHessid = (EditText) findViewById(R.id.editCarPlayHessid);
        buttonCarPlay = (Button) findViewById(R.id.buttonCarPlay);
        buttonCarPlay.setOnClickListener(this);

        //Csi relative
        buttonCsiStop = (Button) findViewById(R.id.buttonCsiStop);
        buttonCsiStop.setOnClickListener(this);
		buttonCsiStop.setVisibility(View.GONE);

        mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onClick(View view) {
        if (mUniqueInstance == null) {
            showMessage("uniqueInstance is null");
            Log.e(TAG, "Failed to get QtiWifiManager instance");
            return;
        }

        if (view == buttonCommand) {
            String command = editTextCommand.getText().toString();
            String reply = "";
            String[] params = command.split("\\s+");
            int size = params.length;
            if (size == 0) {
                reply = COMMAND_RESULT_INVALID_COMMAND;
            } else if (params[0].equals(COMMAND_GET_AVAILABLE_INTERFACES)) {
                List<String> ifaces = mUniqueInstance.getAvailableInterfaces();
                if (ifaces != null) {
                    reply = ifaces.toString();
                }
            } else if (params[0].equals(COMMAND_GET_THERMAL_INFO)) {
                reply = getThermalInfo(params);
            } else if (params[0].equals(COMMAND_REGISTER_VENDOR_EVENT_CALLBACK)) {
                mUniqueInstance.registerVendorEventCallback(mVendorEventCallback, null);
                reply = "OK";
            } else if (params[0].equals(COMMAND_UNREGISTER_VENDOR_EVENT_CALLBACK)) {
                mUniqueInstance.unregisterVendorEventCallback(mVendorEventCallback);
                reply = "OK";
            } else if (params[0].equals(COMMAND_SET_TXPOWER)) {
                reply = setTxPower(params);
            } else if (params[0].equals(COMMAND_SET_ANI)) {
                reply = setAni(params);
            } else if (params[0].equals(COMMAND_SET_CARPLAY)){
                reply = setCarPlayIE(params);
            } else if (params[0].equals(COMMAND_SET_CONGESTION)) {
                reply = setCongestion(params);
            } else if (params[0].equals(COMMAND_START_CSI)) {
                //starting service
                if (!mWifiManager.isWifiEnabled()) {
                    reply = "Turn on Wifi before capturing CSI data";
                    Log.e(TAG, "Turn on Wifi before capturing CSI data");
                } else if (mWifiManager.getConnectionInfo().getSupplicantState() != SupplicantState.COMPLETED) {
                    reply = "Wifi is not connected, CSI not started";
                } else {
                    reply = "CSI start until user stops";
                    mUniqueInstance.startCsi(mCsiCallback, null);
                    buttonCommand.setVisibility(View.GONE);
                    buttonCsiStop.setVisibility(View.VISIBLE);
                    try {
                        fileout=openFileOutput("mytextfile.txt", MODE_PRIVATE);
                        outputWriter=new OutputStreamWriter(fileout);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open file");
                    }
                }
            } else {
                reply = COMMAND_RESULT_INVALID_COMMAND;
            }
            reply = "result of " + command + ":\n" + reply;
            textViewCommand.setText(reply);
        } else if (view == buttonCarPlay) {
            String reply = "";
            String vendorIE = editTextCarPlayVendorIE.getText().toString();
            String assocRespElement = editTextCarPlayAssocRespElement.getText().toString();
            String accessNetworkType = editTextCarPlayAccessNetworkType.getText().toString();
            String esr = editTextCarPlayEsr.getText().toString();
            String internet = editTextCarPlayInternet.getText().toString();
            String venueType = editTextCarPlayVenueType.getText().toString();
            String venueGroup = editTextCarPlayVenueGroup.getText().toString();
            String hessid = editCarPlayHessid.getText().toString();
            Log.d(TAG, "VendorIE: " + vendorIE + ", accessNetworkType: " + accessNetworkType +
                       ", assocRespElement: " + assocRespElement + ", esr: " + esr + ", internet: "
                       + internet + ", venueType: " + venueType + ", venueGroup: " + venueGroup +
                       ", hessid: " + hessid);
            setCarPlayToolBarVisible(false);
            mCarPlayIEData.setVendorIE(vendorIE);
            mCarPlayIEData.setAssocRespElement(assocRespElement);
            mCarPlayIEData.setAccessNetworkType(accessNetworkType);
            mCarPlayIEData.setEsr(esr);
            mCarPlayIEData.setInternet(internet);
            mCarPlayIEData.setVenueType(venueType);
            mCarPlayIEData.setVenueGroup(venueGroup);
            mCarPlayIEData.setHessid(hessid);
            reply = "Set parameters completely.";
            textViewCommand.setText(reply);
        } else if (view == buttonCsiStop) {
            //stopping service
            String reply = "CSI stopped";
            mUniqueInstance.stopCsi(mCsiCallback);
            buttonCommand.setVisibility(View.VISIBLE);
			buttonCsiStop.setVisibility(View.GONE);
            textViewCommand.setText(reply);
            try {
                outputWriter.close();
                Log.i(TAG, "Succseefully close the file");
            } catch (IOException e) {
                Log.e(TAG, "Failed to close the file");
            }
        }
    }

    private String getThermalInfo(String[] params) {
        if (params.length < 2) {
            return COMMAND_RESULT_INVALID_ARGS;
        }
        ThermalData data = mUniqueInstance.getThermalInfo(params[1]);
        if (data == null) {
            return COMMAND_RESULT_FAILED;
        }
        String reply = "temperature=" + data.getTemperature()
                     + " level="+ data.getThermalLevel();
        return reply;
    }

    private String setCarPlayIE(String[] params) {
        boolean setResult = false;
        if (params.length < 2) {
            return COMMAND_RESULT_INVALID_ARGS;
        }

        if (params[1].equals("enable")) {
            Log.d(TAG, "setCarPlayIE: ");
            setResult = mUniqueInstance.enableCarPlayIE(mCarPlayIEData);
            if (setResult == true) {
                return COMMAND_RESULT_SUCCESS;
            } else {
                Log.e(TAG, "AP is not enable, can't set carplay ie, please enable AP at first.");
                String reply = "AP is not enable, can't set carplay ie, please enable AP at first";
                return reply;
            }
        } else if (params[1].equals("set")) {
            Log.d(TAG, "Set CarPlay relative tool bar visible");
            setCarPlayToolBarVisible(true);
            String reply = "Set CarPlay relative parameters.";
            return reply;
        } else if (params[1].equals("disable")) {
            Log.d(TAG, "Disable CarPlay");
            setResult = mUniqueInstance.disableCarPlayIE();
            if (setResult == true) {
                return COMMAND_RESULT_SUCCESS;
            } else {
                 String reply = "Fail to disable CarPlayIE";
                 return reply;
            }
        } else {
            Log.e(TAG, "parameter " + params[1] + " not support now!");
            String reply = "parameter " + params[1] + " not support now!";
            return reply;
        }
    }

    private void setCarPlayToolBarVisible(boolean visible) {
        if (visible == true) {
            editTextCarPlayVendorIE.setVisibility(View.VISIBLE);
            editTextCarPlayAssocRespElement.setVisibility(View.VISIBLE);
            editTextCarPlayAccessNetworkType.setVisibility(View.VISIBLE);
            editTextCarPlayEsr.setVisibility(View.VISIBLE);
            editTextCarPlayInternet.setVisibility(View.VISIBLE);
            editTextCarPlayVenueType.setVisibility(View.VISIBLE);
            editTextCarPlayVenueGroup.setVisibility(View.VISIBLE);
            editCarPlayHessid.setVisibility(View.VISIBLE);
            buttonCommand.setVisibility(View.GONE);
            buttonCarPlay.setVisibility(View.VISIBLE);
        } else {
            editTextCarPlayVendorIE.setVisibility(View.GONE);
            editTextCarPlayAssocRespElement.setVisibility(View.GONE);
            editTextCarPlayAccessNetworkType.setVisibility(View.GONE);
            editTextCarPlayEsr.setVisibility(View.GONE);
            editTextCarPlayInternet.setVisibility(View.GONE);
            editTextCarPlayVenueType.setVisibility(View.GONE);
            editTextCarPlayVenueGroup.setVisibility(View.GONE);
            editCarPlayHessid.setVisibility(View.GONE);
            buttonCommand.setVisibility(View.VISIBLE);
            buttonCarPlay.setVisibility(View.GONE);
        }
    }

    private String setTxPower(String[] params) {
        if (params.length < 3) {
            return COMMAND_RESULT_INVALID_ARGS;
        }

        try {
            String ifname = params[1];
            int dbm = Integer.parseInt(params[2]);
            boolean res = mUniqueInstance.setTxPower(ifname, dbm);
            if (!res) {
                return COMMAND_RESULT_FAILED;
            }
        } catch (NumberFormatException e) {
            return COMMAND_RESULT_INVALID_ARGS;
        }

        return COMMAND_RESULT_SUCCESS;
    }

    private String setAni(String[] params) {
        if (params.length < 3) {
            return COMMAND_RESULT_INVALID_ARGS;
        }

        String ifname = params[1];
        String mode = params[2];
        int modeVal = 0;
        int ofdmlvl = -1;

        if ("auto".equals(mode)) {
            if (params.length > 3) {
                Log.v(TAG, "In auto mode, ofmdlvl will be ignored.");
            }
        } else if ("fixed".equals(mode)) {
            modeVal = 1;
            if (params.length == 3) {
                Log.v(TAG, "In fixed mode, ofmdlvl is required.");
                return COMMAND_RESULT_INVALID_ARGS;
            } else {
                try {
                    ofdmlvl = Integer.parseInt(params[3]);
                } catch (Exception e) {
                    Log.e(TAG, "ofdmlvl must be integer" + params[3]);
                    return COMMAND_RESULT_INVALID_ARGS;
                }
            }
        } else {
            return COMMAND_RESULT_INVALID_ARGS;
        }

        boolean res = mUniqueInstance.setAni(ifname, modeVal, ofdmlvl);
        if (!res) {
            return COMMAND_RESULT_FAILED;
        }

        return COMMAND_RESULT_SUCCESS;
    }

    private String setCongestion(String[] params) {
        if (params.length < 3) {
            return COMMAND_RESULT_INVALID_ARGS;
        }

        String ifname = params[1];
        String enable = params[2];
        int enableInt = -1;
        int threshold = -1;
        int interval = -1;

        if ("disable".equals(enable)) {
            if (params.length > 3) {
                Log.v(TAG, "When disable, threshold/interval will be ignored.");
            }
            enableInt = 0;
        } else if ("enable".equals(enable)) {
            enableInt = 1;
            if (params.length < 5) {
                Log.v(TAG, "When enable, threshold and interval are required.");
                return COMMAND_RESULT_INVALID_ARGS;
            } else {
                try {
                    threshold = Integer.parseInt(params[3]);
                    interval = Integer.parseInt(params[4]);
                } catch (Exception e) {
                    Log.e(TAG, "threshold/interval must be integer"
                          + params[3] + " " + params[4]);
                    return COMMAND_RESULT_INVALID_ARGS;
                }
            }
        } else {
            return COMMAND_RESULT_INVALID_ARGS;
        }

        boolean res = mUniqueInstance.setCongestionReport(ifname, enableInt, threshold, interval);
        if (!res) {
            return COMMAND_RESULT_FAILED;
        }

        return COMMAND_RESULT_SUCCESS;
    }

    public static void unbindService(Context context) {
        QtiWifiManager.unbindService(context);
    }

    @Override
    protected void onDestroy() {
        Log.i("MAINACT", "onDestroy!");
        super.onDestroy();
        try {
            unbindService(getApplicationContext());
        } catch (IllegalArgumentException e) {
            Log.e(TAG,"Illegal Argument Exception ",e);
        }
    }

    private void quitApplicationWithAlert(int title, int message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which)  {
                        // Exit the application.
                        finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
    }
}
