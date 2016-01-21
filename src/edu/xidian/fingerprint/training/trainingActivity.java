package edu.xidian.fingerprint.training;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.logging.LogManager;
import org.altbeacon.beacon.logging.Loggers;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import edu.xidian.FindBeacons.FindBeacons;
import edu.xidian.FindBeacons.FindBeacons.OnBeaconsListener;
import edu.xidian.FindBeacons.RssiDbManager;
import edu.xidian.FindBeacons.RssiInfo;
import edu.xidian.logtofile.LogcatHelper;

/**
 * 实现指纹算法第一阶段，即离线训练阶段。在每个定位参考点收集各个beacons的rssi值，并将其平均值记录至数据库。
 * 在每个参考点停留“rssi采样周期”指定的时间(SamplePeroid)，将这段时间的各个beacons平均值记录至数据库。
 * 数据库管理：edu.xidian.FindBeacons.RssiDbManager，数据库文件：sdcard/rssiRecord/rssi.db
 */
public class trainingActivity extends Activity {
	private final static String TAG = trainingActivity.class.getSimpleName();
    private FindBeacons mFindBeacons;
    
    // 每次扫描周期结束，执行此回调，获取附近beacons信息
    private OnBeaconsListener mBeaconsListener = new OnBeaconsListener() {
		@Override
		public void getBeacons(Collection<Beacon> beacons) {
			// 防止停止收集beacons前，再次收到此回调，导致重复记录，在开始和结束监控（查找）beacons时设置。
			if (isRecorded) return; // 已经记录了
			
			// 日志记录和屏幕显示Beacon信息
			// 有可能到达采样周期时，没有找到所有beacons，甚至是0个beacons，因此，应重复记录，一直到采样周期结束。当然是最后更新的有效。
			String str = "beacons=" + beacons.size();
			LogManager.d(TAG,str);
			logToDisplay(str);
			String rssi;
			for (Beacon beacon : beacons) {
				// becaon的两个id(major,minor)，rssi及其平均值
				str = beacon.getId2()+":"+beacon.getId3()+"="+beacon.getRssi()+","+String.format("%.2f", beacon.getRunningAverageRssi());
				LogManager.d(TAG, str);
				logToDisplay(str);
				
				// 记录至mBeaconsRssi
				rssi = beacon.getId2()+"_"+beacon.getId3()+":"+String.format("%.2f", beacon.getRunningAverageRssi());
				mBeaconsRssi.put(beacon, rssi); 
			}
					
			// 记录参考点的各个beacon的id和rssi平均值
			if ((System.currentTimeMillis()-startSample) >= SamplePeroid) {	
				// 将目前定位参考点测量的各个beacons的rssi平均值计入数据库。
			    SaveRssiToDb();
			    
				str = "记录完毕，定位参考点["+reference_pointPerf+reference_pointNum+"]"+"各个beacon的rssi平均值";
				LogManager.d(TAG, str);
				logToDisplay(str);
			    // 以下必须在UI现成中执行，否则，程序将异常终止。 
			    runOnUiThread(new Runnable() {
		    		public void run() {
		    			String str = "记录完毕，定位参考点["+reference_pointPerf+reference_pointNum+"]";
						Toast.makeText(trainingActivity.this, str, Toast.LENGTH_LONG).show();
						// 下一个参考点默认名称
						reference_pointNum++;
						reference_point_edit.setText(reference_pointPerf+reference_pointNum);  
						
						// 停止查找beacons
						onMonitoringStop(null);
		    	    }
		    	});
			}
		}
    	
    }; 
    
    // 将目前定位参考点测量的各个beacons的rssi平均值计入数据库。
    private void SaveRssiToDb()
    {
    	String RPname = reference_point_edit.getText().toString();
    	String RPrssis="";
    	int i=0;
    	int size = mBeaconsRssi.size();
    	
    	for(String rssi : mBeaconsRssi.values()){
    		i++;
    		if (i < size) RPrssis += rssi + ",";
    		else RPrssis += rssi;
    	}
    	
    	RssiInfo rssiInfo = new RssiInfo(RPname,RPrssis);
		mRssiDbManager.SetRssiInfo(rssiInfo);
		
		// 清空，供下个参考点使用
		mBeaconsRssi.clear();
    }
    
    private static LogcatHelper loghelper;  //日志文件
    private Button start_logfile; // 开始记录日志文件
    private Button end_logfile;   // 停止日志文件
    private String Logformat = "";  // 日志拟制符格式
    
    private Button mStart_btn;  // 开始监控(查找)beacons
    private Button mStop_btn;   // 停止监控(查找)beacons
    
    private EditText ScanPeriod_edit;  // 前台扫描周期
    
    /** rssi采样周期,即，计算该时间段内的平均RSSI（首末各去掉10%）*/
    private EditText SamplePeriod_edit;
    private int SamplePeroid; // ms, rssi采样周期，也是在每个定位参考点的停留时间
    
    /** 记录在每个定位参考点开始采样时间  */
    private long startSample; 
    
    /** 
     * 定位参考点名称
     * 默认名称：reference_pointPerf + reference_pointNum 
     */
    private String reference_pointPerf = "RP"; 
    private int reference_pointNum = 1;
    private EditText reference_point_edit;
    
    /**
     * 标志，用于在OnBeaconsListener中，防止在停止查找beacon前，再次收到回调，重复记录beacon的平均值.
     * 在onMonitoringStart()和onMonitoringStop()中设置，在OnBeaconsListener中判断
     * true: 已经记录;  false: 未记录
     */
    private boolean isRecorded = false;
    
    /** 数据库管理 */
    private RssiDbManager mRssiDbManager;
    
    /** 
     * 记录某定位参考点测量的各个beacons的rssi平均值
     * key Beacon
     * value: major_minor:rssi,major_minor:rssi...
     */
    private Map<Beacon,String> mBeaconsRssi = new HashMap<Beacon,String>(); 
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// 建议使用org.altbeacon.beacon.logging.LogManager.javaLogManager输出日志，altbeacon就是使用这种机制，便于发布版本时，减少输出日志信息。
		// 输出所有ERROR(Log.e()), WARN(Log.w()), INFO(Log.i()), DEBUG(Log.d()), VERBOSE(Log.v())
		// 对应日志级别由高到低
        LogManager.setLogger(Loggers.verboseLogger());
		
        // 全部不输出，在release版本中设置
        //LogManager.setLogger(Loggers.empty());
		
        // 输出ERROR(Log.e()), WARN(Log.w()),缺省状态，仅输出错误和警告信息，即输出警告级别以上的日志
        //LogManager.setLogger(Loggers.warningLogger());
        
        // 试验日志输出
//        LogManager.e(TAG,"Error");
//        LogManager.w(TAG,"Warn");
//        LogManager.i(TAG,"info");
//        LogManager.d(TAG,"debug");
//        LogManager.v(TAG,"verbose");

		LogManager.d(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// 日志文件
		start_logfile = (Button)findViewById(R.id.start_log);
		end_logfile = (Button)findViewById(R.id.end_log);
		
		// 设置SD卡中的日志文件,sd卡根目录/rssiRecord/mydistance.log
		//loghelper = LogcatHelper.getInstance(this,"rssiRecord","mydistance.log");
		// 设置SD卡中的日志文件,sd卡根目录/mydistance.log
		loghelper = LogcatHelper.getInstance(this,"","mydistance.log");
		
		// 打印D级以上(包括D,I,W,E,F)的TAG，其它tag不打印
		//Logformat = TAG + ":D *:S";
		
		// 打印D级以上的TAG，和LogcatHelper全部，其它tag不打印
		//Logformat = TAG + ":D LogcatHelper:V *:S";
		
		// 打印D以上的TAG和RunningAverageRssiFilter，其他tag不打印(*:S)
		//Logformat = TAG + ":D RunningAverageRssiFilter:D *:S";
		
		// 打印D以上的TAG和RssiDbManager，其他tag不打印(*:S)
		Logformat = TAG + ":D RssiDbManager:D *:S";
		
		// 打印D以上的FindBeacons，其他tag不打印(*:S)
		// Logformat = "FindBeacons:D *:S";
		
		//Logformat = "RangedBeacon:V *:S";
		
		// 打印所有日志， priority=V | D | I | W | E ,级别由低到高
		// Logformat = "";
		
		// 日志文件
		loghelper.start(Logformat);  
		
		// "开始记录日志"按钮失效,此时已经开始记录日志
		start_logfile.setEnabled(false);
		
		// 开始/停止监控（查找）beacons
		mStart_btn = (Button)findViewById(R.id.Mstart);
		mStop_btn = (Button)findViewById(R.id.Mstop);
		mStop_btn.setEnabled(false);
				
		// 获取FindBeacons唯一实例
		mFindBeacons = FindBeacons.getInstance(this);
                
    	// 设置默认前台扫描周期,default 1.1s
		ScanPeriod_edit = (EditText)findViewById(R.id.ScanPeriod_edit);
        ScanPeriod_edit.setText("1.1");
        onForegroundScanPeriod(null);
        
        // rssi采样周期,即，计算该时间段内的平均RSSI（首末各去掉10%）,缺省是20秒(20000毫秒)
        SamplePeriod_edit = (EditText)findViewById(R.id.SamplePeriod_edit);
        SamplePeriod_edit.setText("60");  // 在此设定停留时间，默认为1分钟。
        onSamplePeriod(null);
        
        // 定位参考点名称
        reference_point_edit = (EditText)findViewById(R.id.RPname);
        reference_point_edit.setText(reference_pointPerf+reference_pointNum);
        
        // 数据库管理
        mRssiDbManager = new RssiDbManager(trainingActivity.this);
        
        // 设置获取附近所有beacons的监听对象，在每个扫描周期结束，通过该接口获取找到的所有beacons
        mFindBeacons.setBeaconsListener(mBeaconsListener);
             
    	// 查看手机蓝牙是否可用,若当前状态为不可用，则默认调用意图请求打开系统蓝牙
    	mFindBeacons.checkBLEEnable();

        logToDisplay("Mstart,Mstop分别代表查找beacon的开始和结束");
	}
	
    @Override 
    protected void onDestroy() {
    	LogManager.d(TAG,"onDestroy()");
        super.onDestroy();
        
        mFindBeacons.closeSearcher(); 
        loghelper.stop();
    }
    
    /** 开始记录日志文件 */
    public void onStartLog(View view) {
    	loghelper.start(Logformat);  
    	start_logfile.setEnabled(false);
    	end_logfile.setEnabled(true);
    }
    
    /** 结束记录日志文件 */
    public void onEndLog(View view) {
    	loghelper.stop();
    	start_logfile.setEnabled(true);
    	end_logfile.setEnabled(false);
    }
    
    /** 删除日志文件文件 */
    public void onDelLog(View view) {
    	loghelper.delLogDir();
    }
       
    /** 开始查找附近beacons */
    public void onMonitoringStart(View view) {
    	logToDisplay("onMonitoringStart(),startMonitoringBeaconsInRegion");
    	LogManager.d(TAG,"onMonitoringStart(),startMonitoringBeaconsInRegion");
    	
    	// 根据编辑框，设置前台扫描周期,default 1.1s
    	onForegroundScanPeriod(null);
    	        
        // 根据编辑框，设置rssi采样周期,即，计算该时间段内的平均RSSI（首末各去掉10%）,缺省是20秒(20000毫秒)
    	onSamplePeriod(null);
    	
    	mFindBeacons.openSearcher();
    	mStart_btn.setEnabled(false);
    	mStop_btn.setEnabled(true);
    	
    	// 记录开始采样时间
    	startSample = System.currentTimeMillis();
    	
    	// 设置记录标志
    	isRecorded = false;
    }
    
    /** 停止查找beacons */
    public void onMonitoringStop(View view) {
    	logToDisplay("onMonitoringStop(),stopMonitoringBeaconsInRegion");
    	LogManager.d(TAG,"onMonitoringStop(),stopMonitoringBeaconsInRegion");
    	mFindBeacons.closeSearcher();
    	mStart_btn.setEnabled(true);
    	mStop_btn.setEnabled(false);
    	
    	// 设置记录标志
    	isRecorded = true;
    }
    
    /** 设置前台扫描周期 */
    public void onForegroundScanPeriod(View view) {
    	String period_str = ScanPeriod_edit.getText().toString();
        long period = (long)(Double.parseDouble(period_str) * 1000.0D);
        mFindBeacons.setForegroundScanPeriod(period);   
    }
    
    /** 
     * 设置rssi采样周期,即，计算该时间段内的平均RSSI（首末各去掉10%）,缺省是20秒(20000毫秒)
     */
    public void onSamplePeriod(View view) {
    	String period_str = SamplePeriod_edit.getText().toString();
    	SamplePeroid = (int)(Double.parseDouble(period_str) * 1000.0D);
        FindBeacons.setSampleExpirationMilliseconds(SamplePeroid);   
    }
     
    public void logToDisplay(final String line) {
    	runOnUiThread(new Runnable() {
    		Date date = new Date(System.currentTimeMillis());
    		SimpleDateFormat sfd = new SimpleDateFormat("HH:mm:ss.SSS",Locale.CHINA);
	    	String dateStr = sfd.format(date);
    	    public void run() {
    	    	TextView editText = (TextView)trainingActivity.this.findViewById(R.id.monitoringText);
       	    	editText.append(dateStr+"=="+line+"\n");            	    	    		
    	    }
    	});
    }
}
