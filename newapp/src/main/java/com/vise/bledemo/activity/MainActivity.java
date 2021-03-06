package com.vise.bledemo.activity;

import android.Manifest;
import android.app.AlertDialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import android.provider.Settings;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vise.baseble.ViseBle;
import com.vise.baseble.model.BluetoothLeDevice;
import com.vise.baseble.utils.BleUtil;
import com.vise.bledemo.R;
import com.vise.bledemo.adapter.DeviceMainAdapter;
import com.vise.bledemo.common.BluetoothDeviceManager;
import com.vise.bledemo.common.ToastUtil;
import com.vise.bledemo.event.ConnectEvent;
import com.vise.bledemo.event.NotifyDataEvent;
import com.vise.log.ViseLog;
import com.vise.log.inner.LogcatTree;
import com.vise.xsnow.event.BusManager;
import com.vise.xsnow.event.Subscribe;
import com.vise.xsnow.permission.OnPermissionCallback;
import com.vise.xsnow.permission.PermissionManager;

import java.util.ArrayList;
import java.util.List;

/**

 */
public class MainActivity extends AppCompatActivity {

    private TextView supportTv;
    private TextView statusTv;
    private ListView deviceLv;
    private TextView emptyTv;
    private TextView countTv;

    private DeviceMainAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ViseLog.getLogConfig().configAllowLog(true);//??????????????????
        ViseLog.plant(new LogcatTree());//??????Logcat????????????
        BluetoothDeviceManager.getInstance().init(this);
        BusManager.getBus().register(this);
        init();
    }

    private void init() {
        supportTv = (TextView) findViewById(R.id.main_ble_support);
        statusTv = (TextView) findViewById(R.id.main_ble_status);
        deviceLv = (ListView) findViewById(android.R.id.list);
        emptyTv = (TextView) findViewById(android.R.id.empty);
        countTv = (TextView) findViewById(R.id.connected_device_count);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, DeviceScanActivity.class);
                startActivity(intent);
            }
        });

        adapter = new DeviceMainAdapter(this);
        deviceLv.setAdapter(adapter);

        deviceLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                BluetoothLeDevice device = (BluetoothLeDevice) adapter.getItem(position);
                if (device == null) return;
                Intent intent = new Intent(MainActivity.this, DeviceControlActivity.class);
                intent.putExtra(DeviceDetailActivity.EXTRA_DEVICE, device);
                startActivity(intent);
            }
        });

//        checkBluetoothPermission();

    }



    @Subscribe
    public void showConnectedDevice(ConnectEvent event) {
        if (event != null) {
            updateConnectedDevice();
            if (event.isDisconnected()) {
                ToastUtil.showToast(MainActivity.this, "Disconnect!");
            }
        }
    }

    @Subscribe
    public void showDeviceNotifyData(NotifyDataEvent event) {
        if (event != null && adapter != null) {
            adapter.setNotifyData(event.getBluetoothLeDevice(), event.getData());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBluetoothPermission();
    }

    @Override
    protected void onDestroy() {
        ViseBle.getInstance().clear();
        BusManager.getBus().unregister(this);
        super.onDestroy();
    }

    /**
     * ??????????????????
     *
     * @param menu ??????
     * @return ????????????????????????
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.about, menu);
        return true;
    }

    /**
     * ????????????????????????
     *
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about://??????
                displayAboutDialog();
                break;
        }
        return true;
    }

    /**
     * ?????????????????????????????????
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == 1) {
                statusTv.setText(getString(R.string.on));
                enableBluetooth();
            }
        } else if (resultCode == RESULT_CANCELED) {
            finish();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    private void onPermissionGranted(String permission) {
        switch (permission) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
                    new AlertDialog.Builder(this)
                            .setTitle("")
                            .setMessage("")
                            .setNegativeButton("",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    })
                            .setPositiveButton("",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                            startActivityForResult(intent, 2);
                                        }
                                    })

                            .setCancelable(false)
                            .show();
                }
                break;
        }
    }

    /**
     * ??????????????????
     */
    private void checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //???????????????????????????????????????
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                PermissionManager.instance().with(this).request(new OnPermissionCallback() {
                    @Override
                    public void onRequestAllow(String permissionName) {
                        enableBluetooth();
                    }

                    @Override
                    public void onRequestRefuse(String permissionName) {
                        finish();
                    }

                    @Override
                    public void onRequestNoAsk(String permissionName) {
                        finish();
                    }
                }, Manifest.permission.ACCESS_COARSE_LOCATION);
            } else {
                enableBluetooth();
            }
        } else {
            enableBluetooth();
        }
    }

    private void enableBluetooth() {
        if (!BleUtil.isBleEnable(this)) {
            BleUtil.enableBluetooth(this, 1);
        } else {
            boolean isSupport = BleUtil.isSupportBle(this);
            boolean isOpenBle = BleUtil.isBleEnable(this);
            if (isSupport) {
                supportTv.setText(getString(R.string.supported));
            } else {
                supportTv.setText(getString(R.string.not_supported));
            }
            if (isOpenBle) {
                statusTv.setText(getString(R.string.on));
            } else {
                statusTv.setText(getString(R.string.off));
            }
            invalidateOptionsMenu();
            updateConnectedDevice();
        }
    }

    /**
     * ??????????????????????????????
     */
    private void updateConnectedDevice() {
        if (adapter != null && ViseBle.getInstance().getDeviceMirrorPool() != null) {
            List<BluetoothLeDevice> bluetoothLeDeviceList = ViseBle.getInstance().getDeviceMirrorPool().getDeviceList();
            if (bluetoothLeDeviceList != null && bluetoothLeDeviceList.size() > 0) {
                deviceLv.setVisibility(View.VISIBLE);
            } else {
                deviceLv.setVisibility(View.GONE);
            }
            adapter.setListAll(bluetoothLeDeviceList);
            updateItemCount(adapter.getCount());
        } else {
            deviceLv.setVisibility(View.GONE);
        }
    }

    /**
     * ?????????????????????????????????
     *
     * @param count
     */
    private void updateItemCount(final int count) {
        countTv.setText(getString(R.string.formatter_item_count, String.valueOf(count)));
    }

    /**
     * ??????????????????
     */
    private void displayAboutDialog() {
        final int paddingSizeDp = 5;
        final float scale = getResources().getDisplayMetrics().density;
        final int dpAsPixels = (int) (paddingSizeDp * scale + 0.5f);

        final TextView textView = new TextView(this);
        final SpannableString text = new SpannableString("====");

        textView.setText(text);
        textView.setAutoLinkMask(RESULT_OK);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setPadding(dpAsPixels, dpAsPixels, dpAsPixels, dpAsPixels);

        Linkify.addLinks(text, Linkify.ALL);
        new AlertDialog.Builder(this).setTitle(R.string.menu_about).setCancelable(false).setPositiveButton(android.R
                .string.ok, null)
                .setView(textView).show();
    }

}
