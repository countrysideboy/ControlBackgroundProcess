package com.click369.controlbp.service;

import android.app.Application;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.ArraySet;
import android.util.SparseArray;
import android.view.WindowManager;

import com.click369.controlbp.common.Common;
import com.click369.controlbp.util.FileUtil;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by asus on 2017/10/30.
 */
public class XposedAMS {

    static File file_autostart = new File("/data/sys_autostart");
    static File file_setting = new File("/data/sys_setting");
    static File file_control = new File("/data/sys_control");
    static File file_skip = new File("/data/sys_skip");
    static boolean ISAMSHOOK = false;
    static boolean isOneOpen = true;
    static boolean isTwoOpen = true;
    static boolean isAppstart = true;
    static boolean isRecentOpen = true;
    static boolean isSkipAdOpen = true;
    static boolean isPriOpen = true;
    static boolean isStopScanMedia = false;
    static boolean isMubeiStopOther = false;
    static boolean isFloatOk = false;
    static boolean isNeedFloadOnSys = false;
    static boolean isAutoStartNotNotify = false;
    static int MUID = 0;
    final static HashMap<String,Object> appStartPrefHMs = new HashMap<String,Object>();
//    static boolean test = false;
    final static HashSet<Integer> netMobileList = new HashSet<Integer>();
    final static HashSet<Integer> netWifiList = new HashSet<Integer>();

    final static HashSet<String> muBeiHSs = new HashSet<String>();
    final static HashSet<String> notifySkipKeyWords = new HashSet<String>();
    final static HashMap<String,Object> controlHMs = new HashMap<String,Object>();
    final static LinkedHashMap<Long,String> preventPkgs = new LinkedHashMap<Long,String>();
    final static LinkedHashMap<Long,String> killPkgs = new LinkedHashMap<Long,String>();
    final static LinkedHashMap<Long,String> startPkgs = new LinkedHashMap<Long,String>();
    final static HashSet<String> startRuningPkgs = new HashSet<String>();
    final static HashSet<String> notStopPkgs = new HashSet<String>();
    final static HashMap<String,Long> runingTimes = new HashMap<String,Long>();


    final static HashMap<String,HashMap<Long,String>> privacyInfos = new HashMap<String,HashMap<Long,String>>();
    private static String preventInfo = "";
    private static long lastPreventTime = 0;
    private static int preventPkgTime = 0;
    private static String startPkg = "";
    private static String startProc = "";
    private static Object amsObject;
    private static int sysadj = -900;

    private static void initData(Intent intent){
        try {
            Map autoMap = null;
            Map controlMap = null;
            Map settingMap = null;
            Set<String> skipDiaSet = null;
            if(intent!=null){
                autoMap = (Map)intent.getSerializableExtra("autoStartPrefs");
                controlMap = (Map)intent.getSerializableExtra("controlPrefs");
                settingMap = (Map)intent.getSerializableExtra("settingPrefs");
                skipDiaSet = (Set<String>)((Map)intent.getSerializableExtra("skipDialogPrefs")).get(Common.PREFS_SKIPNOTIFY_KEYWORDS);
            }else{
                autoMap = (Map)FileUtil.readObj(file_autostart.getAbsolutePath());
                controlMap = (Map)FileUtil.readObj(file_control.getAbsolutePath());
                settingMap = (Map)FileUtil.readObj(file_setting.getAbsolutePath());
                skipDiaSet = (Set<String>)FileUtil.readObj(file_skip.getAbsolutePath());
            }
            if((settingMap!=null&&settingMap.size()>0)||(autoMap!=null&&autoMap.size()>0)){
                if(intent!=null){
                    FileUtil.writeObj(autoMap,file_autostart.getAbsolutePath());
                    FileUtil.writeObj(settingMap,file_setting.getAbsolutePath());
                    FileUtil.writeObj(controlMap,file_control.getAbsolutePath());
                    FileUtil.writeObj(skipDiaSet,file_skip.getAbsolutePath());
                }
                if(autoMap!=null) {
                    appStartPrefHMs.clear();
                    appStartPrefHMs.putAll(autoMap);
                }
                if(controlMap!=null) {
                    controlHMs.clear();
                    controlHMs.putAll(controlMap);
                }
                if(skipDiaSet!=null){
                    notifySkipKeyWords.clear();
                    notifySkipKeyWords.addAll(skipDiaSet);
                }
                XposedBridge.log("CONTROL_"+appStartPrefHMs.size()+"_"+controlHMs.size()+"_"+notifySkipKeyWords.size()+"_"+settingMap.size());
                isOneOpen = settingMap.containsKey(Common.ALLSWITCH_SERVICE_BROAD)?(boolean)settingMap.get(Common.ALLSWITCH_SERVICE_BROAD):true;//settingPrefs.getBoolean(Common.ALLSWITCH_ONE,true);
                isTwoOpen = settingMap.containsKey(Common.ALLSWITCH_BACKSTOP_MUBEI)?(boolean)settingMap.get(Common.ALLSWITCH_BACKSTOP_MUBEI):true;//settingPrefs.getBoolean(Common.ALLSWITCH_TWO,true);
                isAppstart = settingMap.containsKey(Common.ALLSWITCH_AUTOSTART_LOCK)?(boolean)settingMap.get(Common.ALLSWITCH_AUTOSTART_LOCK):true;//settingPrefs.getBoolean(Common.ALLSWITCH_FIVE,true);
                isRecentOpen = settingMap.containsKey(Common.ALLSWITCH_RECNETCARD)?(boolean)settingMap.get(Common.ALLSWITCH_RECNETCARD):true;//settingPrefs.getBoolean(Common.ALLSWITCH_FOUR,true);
                isSkipAdOpen = settingMap.containsKey(Common.ALLSWITCH_ADSKIP)?(boolean)settingMap.get(Common.ALLSWITCH_ADSKIP):true;//settingPrefs.getBoolean(Common.ALLSWITCH_FOUR,true);
                isPriOpen = settingMap.containsKey(Common.ALLSWITCH_PRIVACY)?(boolean)settingMap.get(Common.ALLSWITCH_PRIVACY):true;//settingPrefs.getBoolean(Common.ALLSWITCH_FOUR,true);
                isStopScanMedia = settingMap.containsKey(Common.PREFS_SETTING_OTHER_STOPSCANMEDIA)?(boolean)settingMap.get(Common.PREFS_SETTING_OTHER_STOPSCANMEDIA):false;//settingPrefs.getBoolean(Common.PREFS_SETTING_OTHER_STOPSCANMEDIA,false);
                isMubeiStopOther = settingMap.containsKey(Common.PREFS_SETTING_ISMUBEISTOPOTHERPROC)?(boolean)settingMap.get(Common.PREFS_SETTING_ISMUBEISTOPOTHERPROC):false;;
                isAutoStartNotNotify = settingMap.containsKey(Common.PREFS_SETTING_ISAUTOSTARTNOTNOTIFY)?(boolean)settingMap.get(Common.PREFS_SETTING_ISAUTOSTARTNOTNOTIFY):false;;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void loadPackage(final XC_LoadPackage.LoadPackageParam lpparam,
                                   final XSharedPreferences settingPrefs,
                                   final XSharedPreferences controlPrefs,
                                   final XSharedPreferences autoStartPrefs,
                                   final XSharedPreferences recentPrefs,
                                   final XSharedPreferences uiBarPrefs,
                                   final XSharedPreferences skipDialogPrefs){

        if(lpparam.packageName.equals("com.android.systemui")) {
            try {
                final Class arCls = XposedUtil.findClass("android.app.Application", lpparam.classLoader);
                Class clss[] = XposedUtil.getParmsByName(arCls,"onCreate");
                if(clss!=null){
                    XC_MethodHook hook = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            try {
                                final Application app = (Application) (methodHookParam.thisObject);
                                if (app!=null){
                                    final Handler handler = new Handler();
                                    final Runnable runnable = new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Intent explicitIntent = new Intent("com.click369.service");
                                                ComponentName component = new ComponentName(Common.PACKAGENAME, Common.PACKAGENAME + ".service.WatchDogService");
                                                explicitIntent.setComponent(component);
                                                app.getApplicationContext().startService(explicitIntent);
                                            }catch (Exception e){
                                                e.printStackTrace();
                                            }
                                        }
                                    };
                                    IntentFilter intentFilter = new IntentFilter();
                                    intentFilter.addAction("com.click369.control.getinitinfo");
                                    intentFilter.addAction("com.click369.control.startservice");
                                    app.registerReceiver(new BroadcastReceiver() {
                                        @Override
                                        public void onReceive(Context context, Intent intent) {
                                            try {
                                                String action = intent.getAction();
                                                if("com.click369.control.getinitinfo".equals(action)){
                                                    autoStartPrefs.reload();
                                                    controlPrefs.reload();
                                                    settingPrefs.reload();
                                                    skipDialogPrefs.reload();
                                                    uiBarPrefs.reload();
                                                    XposedUtil.reloadInfos(context,autoStartPrefs,controlPrefs,settingPrefs,skipDialogPrefs,uiBarPrefs);
                                                }else if("com.click369.control.startservice".equals(action)) {
                                                    long time = 0;
                                                    if(intent.hasExtra("delay")){
                                                        time = intent.getIntExtra("delay",0);
                                                    }
                                                    handler.removeCallbacks(runnable);
                                                    handler.postDelayed(runnable,time);
                                                }
                                            }catch (Throwable e){
                                                e.printStackTrace();
                                            }
                                        }
                                    },intentFilter);
//                                    app.sendBroadcast(new Intent("com.click369.control.getinitinfo"));
//                                    handler.removeCallbacks(runnable);
//                                    handler.postDelayed(runnable,1000);
                                }
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    };
                    XposedUtil.hookMethod(arCls,clss, "onCreate", hook);
                }
            }catch (Throwable e){
                e.printStackTrace();
            }
        }
        if (!lpparam.packageName.equals("android")){
            return;
        }

//        autoStartPrefs.reload();
//        controlPrefs.reload();
//        appStartPrefHMs.putAll(autoStartPrefs.getAll());
//        controlHMs.putAll(controlPrefs.getAll());
        startRuningPkgs.add("android");
        startRuningPkgs.add("com.android.systemui");
//        settingPrefs.reload();
        final Class amsCls = XposedUtil.findClass("com.android.server.am.ActivityManagerService", lpparam.classLoader);
        final Class actServiceCls = XposedUtil.findClass("com.android.server.am.ActiveServices", lpparam.classLoader);
        final Class taskRecordCls = XposedUtil.findClass("com.android.server.am.TaskRecord",lpparam.classLoader);
        final Class processRecordCls = XposedUtil.findClass("com.android.server.am.ProcessRecord",lpparam.classLoader);
        final Class processListCls = XposedUtil.findClass("com.android.server.am.ProcessList",lpparam.classLoader);
        final Class ifwCls = XposedUtil.findClass("com.android.server.firewall.IntentFirewall",lpparam.classLoader);
        final Class amCls = XposedUtil.findClass("android.app.ActivityManager",lpparam.classLoader);
        final Class activityStackSupervisorCls = XposedUtil.findClass("com.android.server.am.ActivityStackSupervisor",lpparam.classLoader);
        final Class nmsCls = XposedUtil.findClass("com.android.server.notification.NotificationManagerService",lpparam.classLoader);
        final Class sysCls = XposedUtil.findClass("com.android.server.SystemService",lpparam.classLoader);
        final Class pwmServiceCls = XposedUtil.findClass("com.android.server.policy.PhoneWindowManager", lpparam.classLoader);
        final Class netServiceCls = XposedUtil.findClass("com.android.server.NetworkManagementService", lpparam.classLoader);
        Class ussClsTemp = XposedUtil.findClass("com.android.server.usage.UsageStatsService", lpparam.classLoader);
        if(android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1){
            ussClsTemp = XposedUtil.findClass("com.android.server.usage.AppStandbyController", lpparam.classLoader);
        }

        final Field sysCxtField = XposedHelpers.findFirstFieldByExactType(amsCls,Context.class);
        final Field mServicesField = XposedHelpers.findFirstFieldByExactType(amsCls,actServiceCls);
        final HashMap<String,Method> amsMethods =  XposedUtil.getAMSParmas(amsCls);
        if (amsMethods.containsKey("finishBooting")){
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam methodHookParam) throws Throwable {
                try {
                    final Object ams = methodHookParam.thisObject;
                    if (sysCxtField != null) {
                        sysCxtField.setAccessible(true);
                        final Context sysCxt = (Context) sysCxtField.get(ams);//(Context)methodHookParam.args[0];
                        Object o = XposedHelpers.getAdditionalStaticField(amsCls, "click369res");
                        boolean isContinue = false;
                        if (o!=null&&sysCxt!=null&&o.hashCode()==sysCxt.hashCode()){
                            isContinue = true;
                        }
                        if (sysCxt != null&&!isContinue) {
                            final Runnable startService = new Runnable() {
                                @Override
                                public void run() {
                                try {
                                    Intent intent = new Intent("com.click369.control.startservice");
                                    intent.putExtra("delay",300);
                                    sysCxt.sendBroadcast(intent);
                                }catch (Throwable e){
                                    e.printStackTrace();
                                }
                                }
                            };
                            final Handler h = new Handler() {
                                @Override
                                public void handleMessage(Message msg) {
                                    try {
                                        String pkg = (String) msg.obj;
                                        Method m = amsCls.getDeclaredMethod("forceStopPackage", String.class, int.class);
                                        m.invoke(ams, pkg, 0);
                                    } catch (Throwable e) {
                                        e.printStackTrace();
                                    }
                                }
                            };
                            if (sysCxt != null) {
                                BroadcastReceiver br = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                    try {
                                        String action = intent.getAction();
                                        if ("com.click369.control.ams.forcestopapp".equals(action)) {
                                            String pkg = intent.getStringExtra("pkg");
                                            if (pkg.equals("com.click369.control") ||
                                                    (pkg.contains("clock") && pkg.contains("android"))) {
                                                return;
                                            }
                                            try {
                                                Message msg = Message.obtain();
                                                msg.obj = pkg;
                                                msg.what = pkg.hashCode();
                                                h.removeMessages(pkg.hashCode());
                                                h.sendMessageDelayed(msg, 100);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                XposedBridge.log("^^^^^^^^^^^^^^hook AMS error " + e + "^^^^^^^^^^^^^^^^^");
                                            }
                                        } else if ("com.click369.control.ams.changerecent".equals(action)) {
                                            String pkg = intent.getStringExtra("pkg");
                                            try {
                                                Field recField = amsCls.getDeclaredField("mRecentTasks");
                                                recField.setAccessible(true);
                                                ArrayList lists = (ArrayList) (recField.get(methodHookParam.thisObject));
                                                if (lists.size() > 0) {
                                                    if (lists.get(0).getClass().getName().equals("com.android.server.am.TaskRecord")) {
                                                        for (Object o : lists) {
                                                            Method getBaseMethod = taskRecordCls.getDeclaredMethod("getBaseIntent");
                                                            if (getBaseMethod == null) {
                                                                getBaseMethod = taskRecordCls.getMethod("getBaseIntent");
                                                            }
                                                            getBaseMethod.setAccessible(true);
                                                            Intent intentm = (Intent) (getBaseMethod.invoke(o));
                                                            if (intentm != null && pkg.equals(intentm.getComponent().getPackageName())) {
                                                                Field isAvailableField = o.getClass().getDeclaredField("isAvailable");
                                                                isAvailableField.setAccessible(true);
                                                                isAvailableField.set(o, true);
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }catch (Exception e) {
                                                e.printStackTrace();
                                                XposedBridge.log("^^^^^^^^^^^^^^^^^changerecent error "+e+" ^^^^^^^^^^^^^^^");
                                            }
                                        }else if ("com.click369.control.ams.delrecent".equals(action)) {
                                            String pkg = intent.getStringExtra("pkg");
                                            try {
                                                Field recField = methodHookParam.thisObject.getClass().getDeclaredField("mRecentTasks");
                                                recField.setAccessible(true);
                                                ArrayList lists = (ArrayList) (recField.get(methodHookParam.thisObject));
                                                if (lists.size() > 0) {
                                                    if (lists.get(0).getClass().getName().equals("com.android.server.am.TaskRecord")) {
                                                        HashSet sets = new HashSet();
                                                        for (Object o : lists) {
                                                            Method getBaseMethod = taskRecordCls.getDeclaredMethod("getBaseIntent");
                                                            if (getBaseMethod == null) {
                                                                getBaseMethod = taskRecordCls.getMethod("getBaseIntent");
                                                            }
                                                            getBaseMethod.setAccessible(true);
                                                            Intent intentm = (Intent) (getBaseMethod.invoke(o));
                                                            if (intentm != null && pkg.equals(intentm.getComponent().getPackageName())) {
                                                                sets.add(o);
                                                            }
                                                        }
                                                        lists.removeAll(sets);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                XposedBridge.log("^^^^^^^^^^^^^^^^^delrecent error "+e+" ^^^^^^^^^^^^^^^");
                                            }
                                        }else if("com.click369.control.ams.forcestopservice".equals(action)){
                                            String pkg = intent.getStringExtra("pkg");
                                            try {
                                                if(pkg==null||
                                                        pkg.equals("android")||
                                                        pkg.startsWith("com.fkzhang")||
                                                        pkg.equals("com.android.settings")){
                                                    return;
                                                }
                                                if (mServicesField!=null){
                                                    mServicesField.setAccessible(true);
                                                    Object mServicesObject = mServicesField.get(ams);
                                                    if (pkg!=null&&pkg.length()>0){
                                                       if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                            Method killMethod = mServicesObject.getClass().getDeclaredMethod("bringDownDisabledPackageServicesLocked", String.class, Set.class, int.class, boolean.class, boolean.class, boolean.class);
                                                            killMethod.setAccessible(true);
                                                            killMethod.invoke(mServicesObject, pkg, null, 0, true, false, true);//第二个布尔值 是停止进程
                                                        }else{
                                                            XposedUtil.stopServicesAndroidL(amsCls,processRecordCls,mServicesObject,ams,pkg);
                                                        }
                                                        muBeiHSs.add(pkg);
                                                        if(isMubeiStopOther){
                                                            XposedUtil.stopProcess(amsCls,ams,pkg);
                                                        }
                                                    }
                                                }
                                            } catch (Throwable e) {
                                                e.printStackTrace();
                                                XposedBridge.log("^^^^^^^^^^^^^^^^^forcestopservice error "+e+" ^^^^^^^^^^^^^^^");
                                            }
                                        }else if("com.click369.control.ams.removemubei".equals(action)) {
                                            String apk = intent.getStringExtra("apk");
                                            muBeiHSs.remove(apk);
                                        }else if("com.click369.control.ams.confirmforcestop".equals(action)||
                                                "com.click369.control.ams.checktimeoutapp".equals(action)){//确认是否杀死该进程
                                            try {
                                                boolean isCheckTimeout = "com.click369.control.ams.checktimeoutapp".equals(action);
                                                long timeout = 0;
                                                if(isCheckTimeout){
                                                    timeout = intent.getLongExtra("timeout",1000*60*60*12L);
                                                }
                                                HashSet<String> pkgs = (HashSet<String>)intent.getSerializableExtra("pkgs");
                                                Field procListField = amsCls.getDeclaredField("mLruProcesses");
                                                procListField.setAccessible(true);
                                                ArrayList procsTemp = (ArrayList)procListField.get(ams);
                                                if(procsTemp!=null&&procsTemp.size()>0){
                                                    HashSet<String> stopPkgs = new HashSet<String>();
                                                    HashSet<String> notstopPkgs = new HashSet<String>();
                                                    HashMap stopProcs = new HashMap();
                                                    for (Object proc:procsTemp){
                                                        Field infoField = proc.getClass().getDeclaredField("info");
                                                        infoField.setAccessible(true);
                                                        Field lastActivityTimeField = proc.getClass().getDeclaredField("lastActivityTime");
                                                        lastActivityTimeField.setAccessible(true);
                                                        Field activitiesField = proc.getClass().getDeclaredField("activities");
                                                        activitiesField.setAccessible(true);
                                                        ApplicationInfo info = (ApplicationInfo)infoField.get(proc);
                                                        if(pkgs.contains(info.packageName)){
                                                            if(isCheckTimeout){
                                                                long lastActivityTime = (Long)lastActivityTimeField.get(proc);
                                                                //超时12小时没有打开过就杀死
                                                                if(SystemClock.uptimeMillis()-lastActivityTime>timeout){
                                                                    Message msg = new Message();
                                                                    msg.obj = info.packageName;
                                                                    h.sendMessage(msg);
                                                                    stopPkgs.add(info.packageName);
                                                                }
                                                            }else{
                                                                ArrayList actList = (ArrayList)activitiesField.get(proc);
                                                                if(actList.size()>0){
                                                                    notstopPkgs.add(info.packageName);
                                                                }else{
                                                                    stopPkgs.add(info.packageName);
                                                                    stopProcs.put(info.packageName,proc);
                                                                }
                                                            }
                                                        }
                                                    }
                                                    stopPkgs.removeAll(notstopPkgs);
                                                    if(stopPkgs.size()>0){
                                                        for(String key:stopPkgs){
                                                            Message msg = new Message();
                                                            msg.obj = key;
                                                            h.sendMessage(msg);
                                                        }
                                                        Intent intent1 = new Intent("com.click369.control.amsstoppkg");
                                                        intent1.putExtra("pkgs",stopPkgs);
                                                        sysCxt.sendBroadcast(intent1);
                                                    }
                                                }
                                            }catch (Throwable e){
                                                e.printStackTrace();
                                            }
                                        }else if("com.click369.control.ams.getprocinfo".equals(action)){
                                            try {
                                                final  HashMap<String,Long> procTimeInfos = new HashMap<String,Long>();
                                                Field procListField = amsCls.getDeclaredField("mLruProcesses");
                                                procListField.setAccessible(true);
                                                ArrayList procs = new ArrayList();
                                                ArrayList procsTemp = (ArrayList)procListField.get(ams);
                                                if(procsTemp!=null&&procsTemp.size()>0){
                                                    procs.addAll(procsTemp);
                                                    for (Object proc:procs){
                                                        Field infoField = proc.getClass().getDeclaredField("info");
                                                        infoField.setAccessible(true);
//                                                        Field interactionEventTimeField = proc.getClass().getDeclaredField("interactionEventTime");
//                                                        interactionEventTimeField.setAccessible(true);
                                                        Field lastActivityTimeField = proc.getClass().getDeclaredField("lastActivityTime");
                                                        lastActivityTimeField.setAccessible(true);
//                                                        Field hasShownUiField = proc.getClass().getDeclaredField("hasShownUi");
//                                                        hasShownUiField.setAccessible(true);
//                                                        Field hasOverlayUiField = proc.getClass().getDeclaredField("hasOverlayUi");
//                                                        hasOverlayUiField.setAccessible(true);

                                                        ApplicationInfo info = (ApplicationInfo)infoField.get(proc);
                                                        if(info.packageName.equals(info.processName)){
                                                            long lastActivityTime = (Long)lastActivityTimeField.get(proc);
//                                                            boolean hasShownUi = (Boolean)hasShownUiField.get(proc);
                                                            procTimeInfos.put(info.packageName,lastActivityTime);
                                                        }
//                                                        long interactionEventTime = (Long)interactionEventTimeField.get(proc);
//                                                        boolean hasOverlayUi = (Boolean)hasOverlayUiField.get(proc);

//                                                        infos.put("interactionEventTime",interactionEventTime);
//                                                        infos.put("lastActivityTime",lastActivityTime);
//                                                        infos.put("hasShownUi",hasShownUi);
//                                                        infos.put("hasOverlayUi",hasOverlayUi);
//                                                        procInfos.put(info.packageName+"+"+info.processName,infos);
//                                                        XposedBridge.log("CONTROL  "+info.packageName+"  "+info.processName+"  "+hasShownUi);
                                                    }
                                                    Intent intent1 = new Intent("com.click369.control.backprocinfo");
                                                    intent1.putExtra("infos",procTimeInfos);
                                                    intent1.putExtra("runtimes",runingTimes);
                                                    sysCxt.sendBroadcast(intent1);
                                                }
                                            }catch (Throwable e){
                                                e.printStackTrace();
                                            }
                                        }else if("com.click369.control.ams.changepersistent".equals(action)){
                                            boolean persistent = intent.getBooleanExtra("persistent", false);
                                            String pkg = intent.getStringExtra("pkg");
                                            boolean iskill = intent.getBooleanExtra("iskill",false);
                                            if(persistent){
                                                notStopPkgs.add(pkg);
                                            }else{
                                                notStopPkgs.remove(pkg);
                                                if(iskill){
                                                    try {
                                                        Method m = amsCls.getDeclaredMethod("forceStopPackage", String.class, int.class);
                                                        m.invoke(methodHookParam.thisObject, pkg, 0);
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                        }else if("com.click369.control.ams.killself".equals(action)){
                                            notStopPkgs.remove(Common.PACKAGENAME);
                                            try {
                                                Method m = amsCls.getDeclaredMethod("forceStopPackage", String.class, int.class);
                                                m.invoke(methodHookParam.thisObject, Common.PACKAGENAME, 0);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }else if(Intent.ACTION_SCREEN_ON.equals(action)){
                                            if(startRuningPkgs.contains(Common.PACKAGENAME)&&Math.random()>0.8) {
                                                Intent intent1 = new Intent("com.click369.control.heart");
                                                sysCxt.sendBroadcast(intent1);
                                                h.postDelayed(startService, 1500);
                                            }else if(!startRuningPkgs.contains(Common.PACKAGENAME)){
                                                h.post(startService);
                                            }
                                        }else if("com.click369.control.ams.heart".equals(action)){
                                            h.removeCallbacks(startService);
                                        }else if("com.click369.control.ams.float.checkxp".equals(action)){
                                            if(intent.hasExtra("isNeedFloadOnSys")){
                                                isNeedFloadOnSys = intent.getBooleanExtra("isNeedFloadOnSys",false);
                                            }
                                            Intent check = new Intent("com.click369.control.float.checkxp");
                                            check.putExtra("isfloatok",isFloatOk&&isNeedFloadOnSys);
                                            sysCxt.sendBroadcast(check);
                                        }else if("com.click369.control.ams.getpreventinfo".equals(action)){
                                            if(intent.hasExtra("isclear")){
                                                preventPkgs.clear();
                                                killPkgs.clear();
                                                startPkgs.clear();
                                            }else{
                                                Intent check = new Intent("com.click369.control.recpreventinfo");
                                                check.putExtra("preventPkgs",preventPkgs);
                                                check.putExtra("killPkgs",killPkgs);
                                                check.putExtra("startPkgs",startPkgs);
                                                sysCxt.sendBroadcast(check);
                                            }
                                        }else if("com.click369.control.ams.reloadskipnotify".equals(action)){
                                            notifySkipKeyWords.clear();
                                            Set<String> sets = (Set<String>)((Map)intent.getSerializableExtra("skipDialogPrefs")).get(Common.PREFS_SKIPNOTIFY_KEYWORDS);
                                            if(sets!=null){
                                                notifySkipKeyWords.addAll(sets);
                                            }
                                        }else if("com.click369.control.ams.checkhook".equals(action)){
                                           Intent intent1 = new Intent("com.click369.control.hookok");
                                           context.sendBroadcast(intent1);
                                        }else if("com.click369.control.ams.initreload".equals(action)){
                                            isNeedFloadOnSys = intent.getBooleanExtra("isNeedFloadOnSys",false);
                                            initData(intent);
                                        }else if("com.click369.control.ams.sendprivacyinfo".equals(action)){
                                           String pkg = intent.getStringExtra("pkg");
                                            HashMap<Long,String> infos = (HashMap<Long,String>)intent.getSerializableExtra("infos");
                                            if(privacyInfos.containsKey(pkg)){
                                                if(privacyInfos.get(pkg).size()>500){
                                                    privacyInfos.get(pkg).clear();
                                                }
                                                privacyInfos.get(pkg).putAll(infos);
                                            }else{
                                                HashMap<Long,String> minfos = new HashMap<Long,String>();
                                                minfos.putAll(infos);
                                                privacyInfos.put(pkg,minfos);
                                            }
//                                            XposedBridge.log("CONTROL_SETPRIVACY "+pkg+"  " + infos.size() + "^^^^^^^^^^^^^^^^^");
                                        }else if("com.click369.control.ams.getprivacyinfo".equals(action)){
                                            String pkg = intent.getStringExtra("pkg");
                                            HashMap<Long,String> minfos =privacyInfos.containsKey(pkg)?privacyInfos.get(pkg):new HashMap<Long,String>();
                                            Intent intent1 = new Intent("com.click369.control.recprivacyinfo");
                                            intent1.putExtra("infos",minfos);
                                            context.sendBroadcast(intent1);
//                                            XposedBridge.log("CONTROL_GETPRIVACY "+pkg+"  " + minfos.size() + "^^^^^^^^^^^^^^^^^");
                                        }else if("com.click369.control.ams.clearprivacyinfo".equals(action)){
                                            String pkg = intent.getStringExtra("pkg");
                                            if(privacyInfos.containsKey(pkg)){
                                                privacyInfos.get(pkg).clear();
                                            }
                                        }
                                    }catch (Throwable e){
                                        e.printStackTrace();
                                        XposedBridge.log("^^^^^^^^^^^^^^AMS广播出错 " + e + "^^^^^^^^^^^^^^^^^");
                                    }
                                    }
                                };
                                amsObject = methodHookParam.thisObject;
                                IntentFilter filter = new IntentFilter();
                                filter.addAction("com.click369.control.ams.forcestopapp");
                                filter.addAction("com.click369.control.ams.changerecent");
                                filter.addAction("com.click369.control.ams.delrecent");
                                filter.addAction("com.click369.control.ams.forcestopservice");
                                filter.addAction("com.click369.control.ams.getprocinfo");
                                filter.addAction("com.click369.control.ams.heart");
                                filter.addAction("com.click369.control.ams.killself");
                                filter.addAction("com.click369.control.ams.removemubei");
                                filter.addAction("com.click369.control.ams.changepersistent");
                                filter.addAction("com.click369.control.ams.initreload");
                                filter.addAction("com.click369.control.ams.reloadskipnotify");
                                filter.addAction("com.click369.control.ams.confirmforcestop");
                                filter.addAction("com.click369.control.ams.checktimeoutapp");
                                filter.addAction("com.click369.control.ams.float.checkxp");
                                filter.addAction("com.click369.control.ams.getpreventinfo");
                                filter.addAction("com.click369.control.ams.checkhook");
                                filter.addAction("com.click369.control.ams.sendprivacyinfo");
                                filter.addAction("com.click369.control.ams.getprivacyinfo");
                                filter.addAction("com.click369.control.ams.clearprivacyinfo");
                                filter.addAction(Intent.ACTION_SCREEN_ON);
                                sysCxt.registerReceiver(br, filter);
                                ISAMSHOOK = true;
                                XposedHelpers.setAdditionalStaticField(amsCls, "click369res", sysCxt.hashCode());
                                XposedBridge.log("CONTROL_BOOTCOMPLETE");
                                initData(null);
                            }
                        }
                    }
                }catch (Throwable e) {
                    XposedBridge.log("^^^^^^^^^^^^^^hook AMS error " + e + "^^^^^^^^^^^^^^^^^");
                }
                }
            };
            XposedUtil.hookMethod(amsCls,amsMethods.get("finishBooting").getParameterTypes(),"finishBooting",hook);
        }else{
            XposedBridge.log("^^^^^^^^^^^^^^finishBooting  函数未找到^^^^^^^^^^^^^^^^^");
        }

        if(amsMethods.containsKey("forceStopPackage")||
                amsMethods.containsKey("killApplication")||
                amsMethods.containsKey("killApplicationProcess")){
            XC_MethodHook  hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    try{
                        String pkgTemp = (String)methodHookParam.args[0];
                        String pkg = (String)methodHookParam.args[0];
                        if(pkg.contains(":")){
                            pkg = pkg.split(":")[0];
                        }
                        if (notStopPkgs.contains(pkg)) {
                            methodHookParam.setResult(null);
                            return;
                        }
                        if(!pkgTemp.contains(":")){
                            if(!preventInfo.startsWith(pkgTemp+"|")){
                                killPkgs.put(System.currentTimeMillis(),pkg);
                            }
                        }
                    } catch (Throwable e) {
                        XposedBridge.log("^^^^^^^^^^^^^^hook AMS forceStopPackage err "+e+"^^^^^^^^^^^^^^^^^");
                    }
                }
            };
            try {
                if(amsMethods.containsKey("forceStopPackage")){
                    final Class clss[] = amsMethods.get("forceStopPackage").getParameterTypes();
                    XposedUtil.hookMethod(amsCls,clss,"forceStopPackage",hook);
                }
               if(amsMethods.containsKey("killApplication")){
                    final Class clss[] = amsMethods.get("killApplication").getParameterTypes();
                    XposedUtil.hookMethod(amsCls,clss,"killApplication",hook);
                }
                if(amsMethods.containsKey("killApplicationProcess")){
                    final Class clss[] = amsMethods.get("killApplicationProcess").getParameterTypes();
                    XposedUtil.hookMethod(amsCls,clss,"killApplicationProcess",hook);
                }
            }catch (Throwable e){
                e.printStackTrace();
            }
        }else{
            XposedBridge.log("^^^^^^^^^^^^^^forceStopPackage  函数未找到^^^^^^^^^^^^^^^^^");
        }
        if(taskRecordCls!=null){
            try {
                XposedUtil.hookMethod(taskRecordCls, XposedUtil.getParmsByName(taskRecordCls, "removeTaskActivitiesLocked"), "removeTaskActivitiesLocked", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Field mAffiliatedTaskIdField = taskRecordCls.getDeclaredField("mAffiliatedTaskId");
                            Field intentField = taskRecordCls.getDeclaredField("intent");
                            mAffiliatedTaskIdField.setAccessible(true);
                            intentField.setAccessible(true);
                            Object intentObject = intentField.get(param.thisObject);
                            String pkg = null;
                            String cls = null;
                            if (intentObject != null) {
                                pkg = ((Intent) intentObject).getComponent().getPackageName();
                                if(pkg == null){
                                    Field affinityField = taskRecordCls.getDeclaredField("affinity");
                                    affinityField.setAccessible(true);
                                    pkg = (String)affinityField.get(param.thisObject);
                                }
                                if(notStopPkgs.contains(pkg)){
                                    param.setResult(null);
                                    return;
                                }
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                });
            }catch (Throwable e){
                e.printStackTrace();
            }
        }
//        if(actServiceCls!=null){
//            try {
//                XposedUtil.hookMethod(actServiceCls, XposedUtil.getParmsByName(actServiceCls, "bringDownServiceLocked"), "bringDownServiceLocked", new XC_MethodHook() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        try {
//                            Object serRec = param.args[0];
//                            Field packageNameField = serRec.getClass().getDeclaredField("packageName");
//                            packageNameField.setAccessible(true);
//                            String pkg = (String) packageNameField.get(serRec);
//                            if (pkg != null) {
//                                if (notStopPkgs.contains(pkg)) {//&&!Common.PACKAGENAME.equals(pkg)
//                                    param.setResult(null);
//                                    return;
//                                }
//                            }
//                        }catch (Exception e){
//                            e.printStackTrace();
//                        }
//                    }
//                });
//            }catch (Throwable e){
//                e.printStackTrace();
//            }
//        }
        //保活自己
        if (processRecordCls!=null){
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    try {
                        Field infoField = processRecordCls.getDeclaredField("info");
                        infoField.setAccessible(true);
                        Field emptyField = processRecordCls.getDeclaredField("empty");
                        emptyField.setAccessible(true);
                        boolean isEmpty = (boolean)emptyField.get(methodHookParam.thisObject);
                        ApplicationInfo info = (ApplicationInfo) infoField.get(methodHookParam.thisObject);
                        String pkg = info.packageName;
//                        XposedBridge.log("CONTROL_KILL_"+pkg+" "+info.processName+" "+isEmpty);
//                        if(!isEmpty){
                            String reason = (String)methodHookParam.args[0];
                            if (notStopPkgs.contains(pkg)&&reason!=null&&!reason.startsWith("stop")) {
                                methodHookParam.setResult(null);
                                return;
                            }
                            if(pkg.equals(info.processName)){
                                startRuningPkgs.remove(pkg);
                                runingTimes.remove(pkg);
                                startPkg = "";
                                startProc = "";
                            }
//                        }
                        if(Common.PACKAGENAME.equals(pkg)){
                            notStopPkgs.remove(pkg);
                            Field field = amsCls.getDeclaredField("mContext");
                            field.setAccessible(true);
                            Context context = (Context) field.get(amsObject);
                            Intent intent = new Intent("com.click369.control.startservice");
                            intent.putExtra("delay",1000);
                            context.sendBroadcast(intent);
                        }
//                        if ((Common.PACKAGENAME.equals(info.packageName) &&
//                                !("killbyself".equals(reason)||"stop com.click369.controlbp".equals(reason)))||
//                                (isNotClean)) {
////                            XposedBridge.log("^^^^^^^^^^^^^^ProcessRecord kill  reason "+methodHookParam.args[0]+"^^^^^^^^^^^^^^^^^");
//                            methodHookParam.setResult(null);
//                            return;
//                        }
//                        if(info.processName.equals(info.packageName)){
//                            startRuningPkgs.remove(info.packageName);
//                            runingTimes.remove(info.packageName);
//                            killPkgs.put(System.currentTimeMillis(),info.packageName);
//                            startPkg = "";
//                            startProc = "";
//                        }
                    }catch (Throwable e){
                        e.printStackTrace();
                    }
                }
            };
            XposedUtil.hookMethod(processRecordCls,XposedUtil.getParmsByName(processRecordCls,"kill"),"kill",hook);
        }

        if(amCls!=null){
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        int uid = (int)param.args[1];
                        if(MUID!=0&&uid == MUID){
                            param.setResult(PackageManager.PERMISSION_GRANTED);
                            return;
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                        XposedBridge.log("checkComponentPermission err "+e.getMessage()+"^^^^^^^^^^^^^^^^^");
                    }

                }
            };
            XposedUtil.hookMethod(amCls, XposedUtil.getParmsByName(amCls, "checkComponentPermission"), "checkComponentPermission",hook);
            XposedUtil.hookMethod(amCls, XposedUtil.getParmsByName(amCls, "checkUidPermission"), "checkUidPermission",hook);
        }

//        if(amsMethods.containsKey("checkCallingPermission")){
//            XC_MethodHook  hook = new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//                    if ("android.permission.FORCE_STOP_PACKAGES".equals(methodHookParam.args[0])) {
//                        try {
//                            methodHookParam.setResult(PackageManager.PERMISSION_GRANTED);
//                            return;
//                        } catch (Throwable e) {
//                            XposedBridge.log("^^^^^^^^^^^^^^hook AMS getpermission err " + lpparam.packageName + "^^^^^^^^^^^^^^^^^");
//                        }
//                    }
//                }
//            };
//            try {
//                final Class clss[] = amsMethods.get("checkCallingPermission").getParameterTypes();
//                XposedUtil.hookMethod(amsCls,clss,"checkCallingPermission",hook);
//            }catch (Throwable e){
//                e.printStackTrace();
//            }
//        }else{
//            XposedBridge.log("^^^^^^^^^^^^^^checkCallingPermission  函数未找到^^^^^^^^^^^^^^^^^");
//        }

//        if(amsMethods.containsKey("isGetTasksAllowed")){
//            XC_MethodHook  hook = new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//                    try {
//                        methodHookParam.setResult(true);
//                        return;
//                    } catch (Throwable e) {
//                        XposedBridge.log("^^^^^^^^^^^^^^hook AM isGetTasksAllowed err ^^^^^^^^^^^^^^^^^");
//                    }
//                }
//            };
//            try {
//                final Class clss[] = amsMethods.get("isGetTasksAllowed").getParameterTypes();
//                XposedUtil.hookMethod(amsCls,clss,"isGetTasksAllowed",hook);
//            }catch (Throwable e){
//                e.printStackTrace();
//            }
//        }else{
//            XposedBridge.log("^^^^^^^^^^^^^^isGetTasksAllowed  函数未找到^^^^^^^^^^^^^^^^^");
//        }
//        isAppstart = settingPrefs.getBoolean(Common.ALLSWITCH_AUTOSTART_LOCK,true);

        //自启控制中自启动
        if(amsMethods.containsKey("startProcessLocked")){
            final int lenTemp = XposedUtil.hook_methodLen(amsCls,"startProcessLocked");
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam methodHookParam) throws Throwable {
                    try{
                        int len = lenTemp<10?6:lenTemp-1;
                        boolean cod1 = methodHookParam.args.length>len&&
                                (methodHookParam.args[1] instanceof ApplicationInfo)&&
                                (methodHookParam.args[4] instanceof String);
                        if(cod1&&methodHookParam.args[1]!=null){
                            String pkg = "";
                            String hostingType = "";
                            ComponentName cn = null;
                            ApplicationInfo applicationInfo = (ApplicationInfo) methodHookParam.args[1];
                            if(applicationInfo.packageName!=null){
                                pkg = applicationInfo.packageName;
                                hostingType = (String)methodHookParam.args[4];
                                if(methodHookParam.args[5] instanceof ComponentName){
                                    cn = (ComponentName) methodHookParam.args[5];
                                }else{
                                    cn = new ComponentName("","");
                                }
                                if(isAppstart){
                                    if(!"android".equals(pkg)){
                                        boolean isPrevent = false;
                                        boolean isAutoHM = appStartPrefHMs.containsKey(pkg+"/autostart")?(boolean)(appStartPrefHMs.get(pkg+"/autostart")):false;
                                        if(isAutoHM&&!pkg.equals(startPkg)){
                                            boolean isContainsPkgRuning = false;
                                            Field procListField = amsCls.getDeclaredField("mLruProcesses");
                                            procListField.setAccessible(true);
                                            ArrayList procsTemp = (ArrayList)procListField.get(methodHookParam.thisObject);
                                            for(Object o:procsTemp){
                                                Field infoField = o.getClass().getDeclaredField("info");
                                                infoField.setAccessible(true);
                                                ApplicationInfo info = (ApplicationInfo)infoField.get(o);
                                                if(pkg.equals(info.packageName)){
                                                    isContainsPkgRuning = true;
                                                    break;
                                                }
                                            }
                                            if(!isContainsPkgRuning&&!"activity".equals(hostingType)){
                                                isPrevent = true;
                                            }else if(cn!=null&&!isContainsPkgRuning){
                                                if (!isContainsPkgRuning&&cn!=null&&
                                                        "activity".equals(hostingType)&&
                                                        (cn.getClassName().contains(".GActivity")||cn.getClassName().contains(".PushActivity")||cn.getClassName().contains(".PushGTActivity"))){
                                                    isPrevent = true;
                                                }else if(appStartPrefHMs.containsKey(pkg+"/checkautostart")){
                                                    Object jumpAct = appStartPrefHMs.get(pkg+"/jumpactivity");
                                                    Object homeAct = appStartPrefHMs.get(pkg+"/homeactivity");
                                                    if(((jumpAct!=null&&jumpAct.equals(cn.getClassName()))||(homeAct!=null&&!homeAct.equals(cn.getClassName())))){
                                                        isPrevent = true;
                                                    }
                                                }
                                            }
                                        }else if(appStartPrefHMs.containsKey(pkg+"/stopapp")&&
                                                (boolean)appStartPrefHMs.get(pkg+"/stopapp")){
                                            isPrevent = true;
                                        }
                                        String saveInfo = pkg+"|"+cn.getClassName()+"|"+methodHookParam.args[0]+"|"+hostingType;//+appStartPrefHMs.size()+test;//+isAutoHM+isPrevent+isContainsPkgRuning;
                                        if(isPrevent){
                                            if(preventPkgs.size()>200||startPkgs.size()>200){
                                                preventPkgs.clear();
                                                startPkgs.clear();
                                                killPkgs.clear();
                                            }
                                            if(("activity".equals(hostingType)||"service".equals(hostingType))&&
                                                    saveInfo.equals(preventInfo)&&
                                                    System.currentTimeMillis()-lastPreventTime<1000){
                                                preventPkgTime++;
                                                if(preventPkgTime>6){
                                                    appStartPrefHMs.remove(pkg+"/autostart");
                                                    preventPkgTime = 0;
                                                    isPrevent = false;
                                                    Intent intent = new Intent("com.click369.control.amsalert");
                                                    intent.putExtra("pkg",pkg);
                                                    intent.putExtra("info","被频繁启动并且频繁阻止，应用控制器在重启手机前取消对其阻止，请检查设置");
                                                    Context sysCxt = (Context) sysCxtField.get(methodHookParam.thisObject);
                                                    sysCxt.sendBroadcast(intent);
                                                }
                                            }else{
                                                preventPkgTime = 0;
                                            }
                                            if(isPrevent){
                                                preventInfo = saveInfo;
                                                //保存 包名:启动的组件的类名:进程名
                                                preventPkgs.put(System.currentTimeMillis(),saveInfo);
                                                lastPreventTime = System.currentTimeMillis();
                                                Method m = amsCls.getDeclaredMethod("forceStopPackage", String.class, int.class);
                                                m.invoke(methodHookParam.thisObject, pkg,0);

                                                methodHookParam.setResult(null);
                                                return;
                                            }
                                        }
                                        if(methodHookParam.args[0]!=null&&!methodHookParam.args[0].equals(startProc)){
                                            startPkgs.put(System.currentTimeMillis(),saveInfo);
                                        }
                                        if(!notStopPkgs.contains(pkg)){
                                            boolean isNotStop = appStartPrefHMs.containsKey(pkg + "/notstop") ? (boolean) (appStartPrefHMs.get(pkg+ "/notstop")) : false;
                                            if(isNotStop){
                                                notStopPkgs.add(pkg);
                                            }
                                            if(Common.PACKAGENAME.equals(pkg)){
                                                notStopPkgs.add(Common.PACKAGENAME);
                                                XposedBridge.log("CONTROL_START_WDS_SUCCESS");
                                            }
                                        }
                                        startPkg = pkg;
                                        preventInfo= "";
                                        startProc = (String)methodHookParam.args[0];
                                    }
                                }
                                startRuningPkgs.add(pkg);
                                if(!runingTimes.containsKey(pkg)){
                                    runingTimes.put(pkg,SystemClock.uptimeMillis());
                                }
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                }
            };
            XposedUtil.hook_methods(amsCls,"startProcessLocked",hook);
        }else{
            XposedBridge.log("^^^^^^^^^^^^^^startProcessLocked  函数未找到^^^^^^^^^^^^^^^^^");
        }
//        isOneOpen = settingPrefs.getBoolean(Common.ALLSWITCH_SERVICE_BROAD,true);
//        isTwoOpen = settingPrefs.getBoolean(Common.ALLSWITCH_BACKSTOP_MUBEI,true);
//        isStopScanMedia = settingPrefs.getBoolean(Common.PREFS_SETTING_OTHER_STOPSCANMEDIA,false);
//        isMubeiStopOther = settingPrefs.getBoolean(Common.PREFS_SETTING_ISMUBEISTOPOTHERPROC,false);
//        if(amsMethods.containsKey("startService")){
//            final Class clss[] = amsMethods.get("startService").getParameterTypes();
//            XC_MethodHook hook = new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//                try{
//                    if(isTwoOpen) {
//                        Intent intent = (Intent) methodHookParam.args[1];
//                        String callingPkg = null;
//                        if (methodHookParam.args[clss.length - 2] instanceof String) {
//                            callingPkg = (String) methodHookParam.args[clss.length - 2];
//                        } else {
//                            for (int i = clss.length - 1; i >= 0; i--) {
//                                if (methodHookParam.args[i] instanceof String) {
//                                    callingPkg = (String) methodHookParam.args[i];
//                                    break;
//                                }
//                            }
//                        }
//                        if ((muBeiHSs.contains(callingPkg) && isTwoOpen)) {
//                            if (intent != null && intent.getComponent() != null && controlHMs.containsKey(intent.getComponent().getClassName() + "/service")) {
//                            } else {
//                                methodHookParam.setResult(intent == null ? new ComponentName("", "") : intent.getComponent());
//                                return;
//                            }
//                        }
//                    }
//                } catch (Throwable e) {
//                    XposedBridge.log("^^^^^^^^^^^^^^AMS阻止服务出错 "+e+ "^^^^^^^^^^^^^^^^^");
//                }
//                }
//            };
//            XposedUtil.hookMethod(amsCls,clss,"startService",hook);
//        }else{
//            XposedBridge.log("^^^^^^^^^^^^^^startService  函数未找到^^^^^^^^^^^^^^^^^");
//        }
//        if(amsMethods.containsKey("bindService")){
//            final Class clss[] = amsMethods.get("bindService").getParameterTypes();
//            XC_MethodHook hook = new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//                try{
//                    if(isAppstart&&clss.length>2&&methodHookParam.args[2] instanceof Intent) {
//                        Intent intent = (Intent) methodHookParam.args[2];
//                        if(intent!=null&&intent.getComponent()!=null){
//                            String pkg = intent.getComponent().getPackageName();
//                            boolean isAutoHM = appStartPrefHMs.containsKey(pkg+"/autostart")?(Boolean)(appStartPrefHMs.get(pkg+"/autostart")):false;
//                            if(isAutoHM&&!startRuningPkgs.contains(pkg)){
//                                String saveInfo = pkg+"|"+intent.getComponent().getClassName()+"|bindservice";
//                                preventPkgs.put(System.currentTimeMillis(),saveInfo);
//                                if(saveInfo.equals(preventInfo)) {
//                                    preventPkgTime++;
//                                    if (preventPkgTime > 6) {
//                                        appStartPrefHMs.remove(pkg + "/autostart");
//                                        preventPkgTime = 0;
//                                        Intent intent1 = new Intent("com.click369.control.amsalert");
//                                        intent1.putExtra("pkg", pkg);
//                                        intent1.putExtra("info", "被频繁启动并且频繁阻止，应用控制器本次取消对其阻止，请检查设置");
//                                        Context sysCxt = (Context) sysCxtField.get(methodHookParam.thisObject);
//                                        sysCxt.sendBroadcast(intent);
//                                    }
//                                }else{
//                                    preventPkgTime = 0;
//                                }
//                                preventInfo = saveInfo;
//                                methodHookParam.setResult(0);
//                                return;
//                            }
//                        }
//                    }
//                } catch (Throwable e) {
//                    XposedBridge.log("^^^^^^^^^^^^^^AMS阻止服务出错 "+e+ "^^^^^^^^^^^^^^^^^");
//                }
//                }
//            };
//            XposedUtil.hookMethod(amsCls,clss,"bindService",hook);
//        }else{
//            XposedBridge.log("^^^^^^^^^^^^^^bindService  函数未找到^^^^^^^^^^^^^^^^^");
//        }

        if(ifwCls!=null){
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        ComponentName cn = (ComponentName)param.args[1];
                        int type = (int)param.args[2];
//                    Intent  intent = (Intent)param.args[3];
//                    String  type = (String)param.args[6];
                        if((type==1||type==2)&&cn!=null&&startRuningPkgs.size()>2){//0activity  1broad  2service
                            String pkg = cn.getPackageName();
                            String cls = cn.getClassName();

                            if(isAppstart){
                                boolean isAutoHM = appStartPrefHMs.containsKey(pkg+"/autostart")?(boolean)(appStartPrefHMs.get(pkg+"/autostart")):false;
//                            XposedBridge.log("IntentFirewall   "+pkg+"  intent:"+intent);
                                if(isAutoHM&&!startRuningPkgs.contains(pkg)){
                                    preventPkgs.put(System.currentTimeMillis(),pkg+"|"+cn.getClassName()+"|."+pkg+"|"+(type==1?"broadcast":"service"));
                                    if(preventPkgs.size()>200||startPkgs.size()>200){
                                        preventPkgs.clear();
                                        startPkgs.clear();
                                        killPkgs.clear();
                                    }
                                    param.setResult(false);
                                    return;
                                }
                            }
                            if(type == 2){
                                if ((controlHMs.containsKey(pkg + "/service")&&((boolean)controlHMs.get(pkg + "/service"))&& isOneOpen)||(muBeiHSs.contains(pkg) && isTwoOpen)) {//||(muBeiHSs.contains(pkg) && isTwoOpen)
                                    if (!controlHMs.containsKey(cls + "/service")) {
                                        param.setResult(false);
                                        return;
                                    }
                                }
                                if (SystemClock.elapsedRealtime() < 1000 * 60 * 2 && isStopScanMedia) {
                                    if (cls.endsWith("MediaScannerService")) {
                                        Method m = amsCls.getDeclaredMethod("forceStopPackage", String.class, int.class);
                                        m.invoke(amsObject, pkg, 0);
                                        param.setResult(false);
                                        return;
                                    }
                                }
                            }else if(type == 1){
                                if ((isOneOpen && controlHMs.containsKey(pkg + "/broad")&&((boolean)controlHMs.get(pkg + "/broad")))||(muBeiHSs.contains(pkg) && isTwoOpen)) {
                                    param.setResult(false);
                                    return;
                                }
                            }
                        }
                    }catch (Throwable e){
                        e.printStackTrace();
                    }
                }
            };
            XposedUtil.hookMethod(ifwCls,XposedUtil.getParmsByName(ifwCls,"checkIntent"),"checkIntent",hook);
        }
        //阻止广播发送相关
//        if(amsMethods.containsKey("broadcastIntentLocked")){
//            XC_MethodHook hook = new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//                try{
//                    if(isOneOpen||isTwoOpen) {
//                        //阻止往出发广播
//                        String callingPackage = ((String) methodHookParam.args[1]) + "";
//                        if (isOneOpen) {
//                            controlPrefs.reload();
//                        }
////                    if((isOneOpen&&controlHMs.containsKey(callingPackage+"/broad"))){
////                       (isMubeiStopOther && isTwoOpen && muBeiHSs.contains(callingPackage)
//                        if ((isOneOpen && controlHMs.containsKey(callingPackage + "/broad")&&
//                                controlHMs.get(callingPackage + "/broad")==(Boolean)true)) {
//                            boolean isSend = false;
//                            if (methodHookParam.args[2] != null) {
//                                Intent intent = (Intent) methodHookParam.args[2];
//                                isSend = (intent.getAction() + "").contains("click369");
//                            }
//                            if (!isSend) {
//                                methodHookParam.setResult(-1);
//                                return;
//                            }
//                        }
//                    }
//                } catch (Throwable e) {
//                    e.printStackTrace();
//                }
//                }
//            };
//            try{
//                final Class clss[] = amsMethods.get("broadcastIntentLocked").getParameterTypes();
//                XposedUtil.hookMethod(amsCls,clss,"broadcastIntentLocked",hook);
//            }catch (Throwable e){
//                e.printStackTrace();
//            }
//        }else{
//            XposedBridge.log("^^^^^^^^^^^^^^broadcastIntentLocked  函数未找到 ^^^^^^^^^^^^^^^^^");
//        }
//        if(true){
//            return;
//        }
        //广播发送相关
        if(amsMethods.containsKey("checkBroadcastFromSystem")){
            //防止系统检测是否是系统广播 不然报异常
            XC_MethodHook hookBroadPerm = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                try{
                    if((isOneOpen||isTwoOpen)&&methodHookParam.args[0]!=null&&methodHookParam.args[0] instanceof Intent){
                        String action =  ((Intent)methodHookParam.args[0]).getAction();
                        if(action!=null&&action.contains("click369")){
                            methodHookParam.setResult(null);
                            return;
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                }
            };
            XposedUtil.hookMethod(amsCls,amsMethods.get("checkBroadcastFromSystem").getParameterTypes(),"checkBroadcastFromSystem",hookBroadPerm);
        }else{
            XposedBridge.log("^^^^^^^^^^^^^^checkBroadcastFromSystem  函数未找到 ^^^^^^^^^^^^^^^^^");
        }

//        isRecentOpen = settingPrefs.getBoolean(Common.ALLSWITCH_RECNETCARD,true);
        //最近任务隐藏相关
        if(amsMethods.containsKey("createRecentTaskInfoFromTaskRecord")){
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                try{
                    if(isRecentOpen) {
                        Object recentObj = methodHookParam.args[0];
                        Method updateMethod = taskRecordCls.getDeclaredMethod("updateTaskDescription");
                        if (updateMethod == null) {
                            updateMethod = taskRecordCls.getMethod("updateTaskDescription");
                        }
                        updateMethod.setAccessible(true);
                        updateMethod.invoke(recentObj);
                        Method getBaseMethod = taskRecordCls.getDeclaredMethod("getBaseIntent");
                        if (getBaseMethod == null) {
                            getBaseMethod = taskRecordCls.getMethod("getBaseIntent");
                        }
                        getBaseMethod.setAccessible(true);
                        Intent intent = (Intent) (getBaseMethod.invoke(recentObj));
                        recentPrefs.reload();
//                    XposedBridge.log("^^^^^^^^^^^^^^createRecentTaskInfoFromTaskRecord intent  "+intent+"  "+recentPrefs.getAll().size()+"^^^^^^^^^^^^^^^^^");
                        if (intent != null && recentPrefs.contains(intent.getComponent().getPackageName() + "/notshow")) {
                            Field isAvailableField = taskRecordCls.getDeclaredField("isAvailable");
                            isAvailableField.setAccessible(true);
                            isAvailableField.set(recentObj, !recentPrefs.getBoolean(intent.getComponent().getPackageName() + "/notshow", false));
                        }
                    }
                }catch (Throwable e){
                    e.printStackTrace();
                }
                }
            };
            XposedUtil.hookMethod(amsCls, amsMethods.get("createRecentTaskInfoFromTaskRecord").getParameterTypes(),"createRecentTaskInfoFromTaskRecord",hook);
        }else{
            XposedBridge.log("^^^^^^^^^^^^^^createRecentTaskInfoFromTaskRecord  函数未找到 ^^^^^^^^^^^^^^^^^");
        }

        //最近任务保留常驻内存
        if(processRecordCls!=null){
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                try{
                    if (isAppstart){
                        Field infoField = processRecordCls.getDeclaredField("info");
                        infoField.setAccessible(true);
                        ApplicationInfo info = (ApplicationInfo)infoField.get(methodHookParam.thisObject);
                        boolean isNotClean = appStartPrefHMs.containsKey(info.packageName+"/notstop")?(boolean)(appStartPrefHMs.get(info.packageName+"/notstop")):false;
                        if (isNotClean||info.packageName.equals(Common.PACKAGENAME)){
                            if(info.packageName.equals(Common.PACKAGENAME)){
                                MUID = info.uid;
                            }
                            try {
                                Field systemNoUiField = processRecordCls.getDeclaredField("systemNoUi");
                                systemNoUiField.setAccessible(true);
                                systemNoUiField.set(methodHookParam.thisObject, true);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                            try {
                                Field hasTopUiField = processRecordCls.getDeclaredField("hasTopUi");
                                hasTopUiField.setAccessible(true);
                                hasTopUiField.set(methodHookParam.thisObject,true);
                            }catch (Exception e){
                                e.printStackTrace();
                            }

                            try {
                                Field foregroundActivitiesField = processRecordCls.getDeclaredField("foregroundActivities");
                                foregroundActivitiesField.setAccessible(true);
                                foregroundActivitiesField.set(methodHookParam.thisObject,true);

                                Field field = processListCls.getDeclaredField("SYSTEM_ADJ");
                                field.setAccessible(true);
                                sysadj = (int)field.get(null);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                            try {
                                Field setAdjField = processRecordCls.getDeclaredField("setAdj");
                                setAdjField.setAccessible(true);
                                setAdjField.set(methodHookParam.thisObject,sysadj);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                            try {
                                Field curAdjField = processRecordCls.getDeclaredField("curAdj");
                                curAdjField.setAccessible(true);
                                curAdjField.set(methodHookParam.thisObject,sysadj);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                            try {
                                Field maxAdjField = processRecordCls.getDeclaredField("maxAdj");
                                maxAdjField.setAccessible(true);
                                maxAdjField.set(methodHookParam.thisObject,sysadj);
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                }catch (Throwable e){
                    e.printStackTrace();
                }
                }
            };
            Constructor cs[] = processRecordCls.getDeclaredConstructors();
            if(cs!=null&&cs.length>0){
                XposedUtil.hookConstructorMethod(processRecordCls,cs[0].getParameterTypes(),hook);
            }
            XposedUtil.hookMethod(processRecordCls, XposedUtil.getParmsByName(processRecordCls, "modifyRawOomAdj"), "modifyRawOomAdj", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (isAppstart){
                            Field emptyField = processRecordCls.getDeclaredField("empty");
                            emptyField.setAccessible(true);
                            boolean isEmpty = (boolean)emptyField.get(param.thisObject);
                            if(!isEmpty){
                                Field infoField = processRecordCls.getDeclaredField("info");
                                infoField.setAccessible(true);
                                ApplicationInfo info = (ApplicationInfo)infoField.get(param.thisObject);
                                boolean isNotClean = appStartPrefHMs.containsKey(info.packageName+"/notstop")?(boolean)(appStartPrefHMs.get(info.packageName+"/notstop")):false;
                                if (isNotClean||info.packageName.equals(Common.PACKAGENAME)){
                                    param.setResult(sysadj);
                                    return;
                                }
                            }
                        }
                    }catch (Throwable e){
                        e.printStackTrace();
                    }
                }
            });
        }else{
            XposedBridge.log("^^^^^^^^^^^^^^ProcessRecord1  构造函数未找到 ^^^^^^^^^^^^^^^^^");
        }
        //        final Method anyTaskForIdLockedMethod = XposedUtil.getMethodByName(activityStackSupervisorCls,"anyTaskForIdLocked");
        //最近任务保留或移除相关
//        if(amsMethods.containsKey("removeTaskByIdLocked")){
        if(activityStackSupervisorCls!=null&&amsMethods.containsKey("removeTask")){
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                    try {
                        if(isRecentOpen||isAppstart) {
                            Field mStackSupervisorField = amsCls.getDeclaredField("mStackSupervisor");
                            mStackSupervisorField.setAccessible(true);
                            Object mStackSupervisorObject = mStackSupervisorField.get(methodHookParam.thisObject);
                            final Method anyTaskForIdLockedMethod = activityStackSupervisorCls.getDeclaredMethod("anyTaskForIdLocked",int.class);
                            anyTaskForIdLockedMethod.setAccessible(true);
                            Object taskRecordObject = anyTaskForIdLockedMethod.invoke(mStackSupervisorObject, methodHookParam.args[0]);
                            if (taskRecordObject != null) {
                                Field mAffiliatedTaskIdField = taskRecordCls.getDeclaredField("mAffiliatedTaskId");
                                Field intentField = taskRecordCls.getDeclaredField("intent");
                                mAffiliatedTaskIdField.setAccessible(true);
                                intentField.setAccessible(true);
                                Object intentObject = intentField.get(taskRecordObject);
                                String pkg = null;
                                String cls = null;
                                if (intentObject != null) {
                                    pkg = ((Intent) intentObject).getComponent().getPackageName();
                                    cls = ((Intent) intentObject).getComponent().getClassName();
                                    if(pkg == null){
                                        Field affinityField = taskRecordCls.getDeclaredField("affinity");
                                        affinityField.setAccessible(true);
                                        pkg = (String)affinityField.get(taskRecordObject);
                                    }
                                    if (recentPrefs.hasFileChanged()) {
                                        recentPrefs.reload();
                                    }
                                }
                                if (pkg != null && recentPrefs.getBoolean(pkg + "/notclean", false)) {

                                    if("com.tencent.mm".equals(pkg)&&!"com.tencent.mm.ui.LauncherUI".equals(cls)){
                                    }else {
                                        methodHookParam.setResult(false);
                                        return;
                                    }
                                } else if (recentPrefs.getBoolean(pkg + "/forceclean", false)) {
                                    try {
                                        //com.tencent.mm.plugin.appbrand.ui.AppBrandUI  com.tencent.mm.ui.LauncherUI
                                        if("com.tencent.mm".equals(pkg)&&!"com.tencent.mm.ui.LauncherUI".equals(cls)){
                                        }else {
                                            methodHookParam.args[1] = true;
                                            Method m = amsCls.getDeclaredMethod("forceStopPackage", String.class, int.class);
                                            m.setAccessible(true);
                                            m.invoke(amsObject, pkg, 0);
                                        }
                                    } catch (RuntimeException e) {
                                        e.printStackTrace();
                                    }
                                    if (sysCxtField != null) {
                                        sysCxtField.setAccessible(true);
                                        final Context sysCxt = (Context) sysCxtField.get(amsObject);//(Context)methodHookParam.args[0];
                                        if (sysCxt != null) {
                                            if("com.tencent.mm".equals(pkg)&&"com.tencent.mm.plugin.appbrand.ui.AppBrandUI".equals(cls)){
                                            }else {
                                                Intent intent = new Intent("com.click369.control.removerecent");
                                                intent.putExtra("pkg", pkg);
                                                sysCxt.sendBroadcast(intent);
                                            }
                                        }
                                    }
                                }
                                if(isAppstart&&methodHookParam.args.length>1&&notStopPkgs.contains(pkg)&&(methodHookParam.args[1] instanceof Boolean)){
                                    methodHookParam.args[1]=false;
                                }
                            } else {
                                XposedBridge.log("^^^^^^^^^^^^^^taskRecordObject removeTask 对象获取失败 ^^^^^^^^^^^^^^^^^");
                            }
                        }
                    } catch (Throwable e) {
                        e.printStackTrace();
                        XposedBridge.log("^^^^^^^^^^^^^^removeTask error0 "+e.getMessage()+" ^^^^^^^^^^^^^^^^^");
                    }
                }
            };
            XposedUtil.hookMethod(amsCls,amsMethods.get("removeTask").getParameterTypes(),"removeTask",hook);
            if(amsMethods.containsKey("removeTaskByIdLocked")){
                XposedUtil.hookMethod(amsCls,amsMethods.get("removeTaskByIdLocked").getParameterTypes(),"removeTaskByIdLocked",hook);
            }else{
                XC_MethodHook hook1 = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        try {
                            if(isAppstart) {
                                final Method anyTaskForIdLockedMethod = activityStackSupervisorCls.getDeclaredMethod("anyTaskForIdLocked",int.class);
                                anyTaskForIdLockedMethod.setAccessible(true);
                                Object taskRecordObject = anyTaskForIdLockedMethod.invoke(methodHookParam.thisObject, methodHookParam.args[0]);
                                if (taskRecordObject != null) {
                                    Field mAffiliatedTaskIdField = taskRecordCls.getDeclaredField("mAffiliatedTaskId");
                                    Field intentField = taskRecordCls.getDeclaredField("intent");
                                    mAffiliatedTaskIdField.setAccessible(true);
                                    intentField.setAccessible(true);
                                    Object intentObject = intentField.get(taskRecordObject);
                                    String pkg = null;
                                    String cls = null;
                                    if (intentObject != null) {
                                        pkg = ((Intent) intentObject).getComponent().getPackageName();
                                        cls = ((Intent) intentObject).getComponent().getClassName();
                                        if(pkg == null){
                                            Field affinityField = taskRecordCls.getDeclaredField("affinity");
                                            affinityField.setAccessible(true);
                                            pkg = (String)affinityField.get(taskRecordObject);
                                        }
                                        if (recentPrefs.hasFileChanged()) {
                                            recentPrefs.reload();
                                        }
                                    }
                                    if(notStopPkgs.contains(pkg)){
                                        methodHookParam.args[1]=false;
                                    }
                                } else {
                                    XposedBridge.log("^^^^^^^^^^^^^^taskRecordObject removeTaskByIdLocked 1 对象获取失败 ^^^^^^^^^^^^^^^^^");
                                }
                            }
                        } catch (Throwable e) {
                            e.printStackTrace();
                            XposedBridge.log("^^^^^^^^^^^^^^removeTaskByIdLocked error1 "+e.getMessage()+" ^^^^^^^^^^^^^^^^^");
                        }
                    }
                };
                XposedUtil.hookMethod(activityStackSupervisorCls,XposedUtil.getMaxLenParmsByName(activityStackSupervisorCls,"removeTaskByIdLocked"),"removeTaskByIdLocked",hook1);
            }
        }

        if(amsMethods.containsKey("startActivityFromRecents")){
            final Class clss[] = amsMethods.get("startActivityFromRecents").getParameterTypes();
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                try {
                    if(isAppstart) {
                        Field mStackSupervisorField = amsCls.getDeclaredField("mStackSupervisor");
                        mStackSupervisorField.setAccessible(true);
                        Object mStackSupervisorObject = mStackSupervisorField.get(methodHookParam.thisObject);

                        final Method anyTaskForIdLockedMethod = activityStackSupervisorCls.getDeclaredMethod("anyTaskForIdLocked",int.class);
                        anyTaskForIdLockedMethod.setAccessible(true);
                        Object taskRecordObject = anyTaskForIdLockedMethod.invoke(mStackSupervisorObject, methodHookParam.args[0]);

                        if (taskRecordObject != null) {
                            Field mAffiliatedTaskIdField = taskRecordCls.getDeclaredField("mAffiliatedTaskId");
                            Field intentField = taskRecordCls.getDeclaredField("intent");
                            mAffiliatedTaskIdField.setAccessible(true);
                            intentField.setAccessible(true);
                            Object intentObject = intentField.get(taskRecordObject);
                            String pkg = null;
                            if (intentObject != null) {
                                pkg = ((Intent) intentObject).getComponent().getPackageName();
                                if(pkg == null){
                                    Field affinityField = taskRecordCls.getDeclaredField("affinity");
                                    affinityField.setAccessible(true);
                                    pkg = (String)affinityField.get(taskRecordObject);
                                }
                            }
                            final Object ams = methodHookParam.thisObject;
                            Field sysCxtField = amsCls.getDeclaredField("mContext");
                            if (sysCxtField != null) {
                                sysCxtField.setAccessible(true);
                                final Context sysCxt = (Context) sysCxtField.get(ams);//(Context)methodHookParam.args[0];
                                if (sysCxt != null) {
                                    //给启动判断时发送广播
                                    Intent broad1 = new Intent("com.click369.control.test");
                                    broad1.putExtra("pkg", pkg);
                                    broad1.putExtra("from", lpparam.packageName);
                                    broad1.putExtra("class", pkg == null ? null : ((Intent) intentObject).getComponent().getClassName().toString());
                                    broad1.putExtra("action", "");
                                    sysCxt.sendBroadcast(broad1);

                                    boolean isLockApp = appStartPrefHMs.containsKey(pkg+"/lockapp")?(boolean)appStartPrefHMs.get(pkg+"/lockapp"):false;
                                    if (pkg != null && isLockApp) {
                                        autoStartPrefs.reload();
                                        if (!autoStartPrefs.getBoolean(pkg + "/lockok", false)) {
                                            Intent intent = new Intent(Intent.ACTION_MAIN);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);// 注意
                                            intent.addCategory(Intent.CATEGORY_HOME);
                                            sysCxt.startActivity(intent);
                                            Intent broad = new Intent("com.click369.control.lockapp");
                                            broad.putExtra("pkg", pkg);
                                            broad.putExtra("intent", intentObject == null ? null : (Intent) intentObject);
                                            sysCxt.startActivity(broad);
                                            methodHookParam.setResult(0);
                                            return;
                                        }
                                    }
                                }
                            }
                        } else {
                            XposedBridge.log("taskRecordObject FromRecents 对象获取失败 ^^^^^^^^^^^^^^^^^");
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                }
            };
            XposedUtil.hookMethod(amsCls,clss,"startActivityFromRecents",hook);
        }else if(isAppstart){
            XposedBridge.log("^^^^^^^^^^^^^^startActivityFromRecents  函数未找到 ^^^^^^^^^^^^^^^^^");
        }



//            try {
//                final Class brCls = XposedHelpers.findClass(" com.android.server.am.BroadcastRecord", lpparam.classLoader);
//                Constructor css[] = brCls.getDeclaredConstructors();
//                if(css!=null){
//                    Class clss[] = null;
//                    for(Constructor con:css){
//                        if(con.getParameterTypes().length>10){
//                            clss = con.getParameterTypes();
//                            break;
//                        }
//                    }
//                    XC_MethodHook hook1 = new XC_MethodHook() {
//                        @Override
//                        protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
//                        try {
//                            if((isOneOpen||isTwoOpen)){
//                            int index = methodHookParam.args[10] instanceof List ?10:methodHookParam.args[11] instanceof List?11:-1;
//                            if(index!=-1){
//                                List receivers = (List)methodHookParam.args[index];
//                                if(receivers!=null&&receivers.size()>0){
////                                    XposedBridge.log("CONTROL -----BroadcastRecord "+receivers.get(0).getClass().getName());
//                                    Set removes = new HashSet();
//                                    for(Object o:receivers){
//                                        if (o.getClass().getName().contains("BroadcastFilter")){
//                                            Field nameFiled= o.getClass().getDeclaredField("packageName");
//                                            nameFiled.setAccessible(true);
//                                            String name = (String)nameFiled.get(o);
//                                            if ((isTwoOpen&&muBeiHSs.contains(name))||
//                                                    (isOneOpen&&controlHMs.containsKey(name+"/broad")&&controlHMs.get(name+"/broad")==(Boolean)true)){
////                                                    if ((isOneOpen&&controlHMs.containsKey(name+"/broad"))){
//                                                removes.add(o);
//                                            }
//                                        }else if(o instanceof ResolveInfo){
//                                            ActivityInfo info = ((ResolveInfo)o).activityInfo;
//                                            String name = info!=null?info.packageName:"";
//                                            if ((isTwoOpen&&muBeiHSs.contains(name))||
//                                                    (isOneOpen&&controlHMs.containsKey(name+"/broad")&&controlHMs.get(name+"/broad")==(Boolean)true)){
//                                                removes.add(o);
//                                            }
//                                        }
//                                    }
//                                    receivers.removeAll(removes);
//                                }
//                            }
//                            }
//                        }catch (Throwable e){
//                            e.printStackTrace();
//                        }
//                        }
//                    };
//                    if (clss!=null){
//                        //让7.0及以下生效  8.0强制杀死后不需要
//                        if(Build.VERSION.SDK_INT<=Build.VERSION_CODES.N ){
//                            XposedUtil.hookConstructorMethod(brCls,clss,hook1);
//                        }
//                    }else{
//                        XposedBridge.log("CONTROL BroadcastRecord 未找到0");
//                    }
//                }else{
//                    XposedBridge.log("CONTROL BroadcastRecord 未找到1");
//                }
//            } catch (Throwable e) {
//                e.printStackTrace();
//            }

        try{
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                final Class ussCls = ussClsTemp;
                if(ussCls!=null){
                    Class idleFilterParmsTemp[] = XposedUtil.getParmsByName(ussCls,"isAppIdleFilteredOrParoled");
                    Method getidlemethodTemp = XposedUtil.getMethodByName(ussCls,"isAppIdleFilteredOrParoled");
                    Method idlemethodTemp = XposedUtil.getMethodByName(ussCls,"setAppIdleAsync");
                    if (idlemethodTemp==null){
                        idlemethodTemp = XposedUtil.getMethodByName(ussCls,"setAppIdle");
                    }
                    if (idlemethodTemp==null){
                        idlemethodTemp = XposedUtil.getMethodByName(ussCls,"setAppInactive");
                    }
                    if(getidlemethodTemp==null){
                        getidlemethodTemp = XposedUtil.getMethodByName(ussCls,"isAppInactive");//isAppInactive
                        idleFilterParmsTemp = XposedUtil.getParmsByName(ussCls,"isAppInactive");
                    }
                    final Method idlemethod = idlemethodTemp;
                    final Method getidlemethod = getidlemethodTemp;
                    final Class idleFilterParms[] = idleFilterParmsTemp;
                    XC_MethodHook hook = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam methodHookParam) throws Throwable {
                            try {
                                Context context = null;
                                if(methodHookParam.args[0] instanceof Context){
                                    context = (Context)methodHookParam.args[0];
                                }else{
                                    Field cxtFiled = methodHookParam.thisObject.getClass().getDeclaredField("mContext");
                                    cxtFiled.setAccessible(true);
                                    context = (Context) cxtFiled.get(methodHookParam.thisObject);
                                }
                                BroadcastReceiver br = new BroadcastReceiver() {
                                    @Override
                                    public void onReceive(Context context, Intent intent) {
                                        if ("com.click369.control.uss.setappidle".equals(intent.getAction())){
                                            try {
                                                Set<String> idlePkgs = new HashSet<String>();
                                                if (intent.hasExtra("pkg")){
                                                    idlePkgs.add(intent.getStringExtra("pkg"));
                                                }else if(intent.hasExtra("pkgs")){
                                                    idlePkgs.addAll((Set<String>)intent.getSerializableExtra("pkgs"));
                                                }
                                                boolean idle = intent.getBooleanExtra("idle",true);
                                                if (idlemethod!=null) {
                                                    idlemethod.setAccessible(true);
                                                    for(String pkg:idlePkgs){
                                                        idlemethod.invoke(methodHookParam.thisObject, pkg, idle, 0);
                                                    }
                                                }else{
                                                    XposedBridge.log("CONTROL -----未找到idle函数  ");
                                                }
                                            } catch (Throwable e) {
                                                e.printStackTrace();
                                                XposedBridge.log("CONTROL -----设置待机出错  "+e.getMessage());
                                            }
                                        }else if ("com.click369.control.uss.getappidlestate".equals(intent.getAction())){
                                            try{
                                                getidlemethod.setAccessible(true);
                                                HashMap<String,Boolean> pkgs = ( HashMap<String,Boolean>)intent.getSerializableExtra("pkgs");
                                                HashSet<String> newpkgs =new HashSet<String>();
                                                Set<String> keys = pkgs.keySet();
                                                if (idleFilterParms!=null&&idleFilterParms.length == 4){
                                                    for(String key:keys){
                                                        Object isIdle = getidlemethod.invoke(methodHookParam.thisObject,key.trim(),0, SystemClock.elapsedRealtime(),true);
                                                        if((Boolean)isIdle==true){
                                                            newpkgs.add(key);
                                                        }
                                                    }
                                                }else if(idleFilterParms!=null&&idleFilterParms.length == 3){
                                                    for(String key:keys){
                                                        Object isIdle = getidlemethod.invoke(methodHookParam.thisObject,key.trim(),0, SystemClock.elapsedRealtime());
                                                        if((Boolean)isIdle==true){
                                                            newpkgs.add(key);
                                                        }
                                                    }
                                                }else if(idleFilterParms!=null&&idleFilterParms.length == 2){
                                                    for(String key:keys){
                                                        Object isIdle = getidlemethod.invoke(methodHookParam.thisObject,key.trim(),0);
                                                        if((Boolean)isIdle==true){
                                                            newpkgs.add(key);
                                                        }
                                                    }
                                                }
                                                Intent intent1 = new Intent("com.click369.control.recappidlestate");
                                                intent1.putExtra("pkgs",newpkgs);
                                                intent1.putExtra("mbpkgs",muBeiHSs);
                                                context.sendBroadcast(intent1);
                                            } catch (Throwable e){
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                };
                                try {
                                    IntentFilter filter = new IntentFilter();
                                    filter.addAction("com.click369.control.uss.setappidle");
                                    filter.addAction("com.click369.control.uss.getappidlestate");
                                    context.registerReceiver(br, filter);
                                }catch (Throwable e){

                                }
                            }catch (Throwable e){
                                e.printStackTrace();
                            }
                        }
                    };
                    if(android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1){
                        //Injector
                        Class injectorCls = XposedUtil.findClass("com.android.server.usage.AppStandbyController$Injector", lpparam.classLoader);
                        XposedHelpers.findAndHookConstructor(ussCls,injectorCls, hook);
                    }else{
                        XposedHelpers.findAndHookConstructor(ussCls, Context.class, hook);
                    }
                }
            }
        }catch (Throwable e) {
            e.printStackTrace();
            XposedBridge.log("CONTROL -----未找到UsageStatsService ClassNotFoundError "+e);
        }
        try {
            if(pwmServiceCls!=null) {
                XposedUtil.hookMethod(pwmServiceCls, XposedUtil.getParmsByName(pwmServiceCls, "checkAddPermission"), "checkAddPermission", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) param.args[0];
                            CharSequence title = attrs.getTitle();
                            if (isNeedFloadOnSys && Common.PACKAGENAME.equals(attrs.packageName) && ("控制器".equals(title))) {
                                param.setResult(0);
                                return;
                            }
                        } catch (Throwable e) {
                            isFloatOk = false;
                            e.printStackTrace();
                        }
                    }
                });
                isFloatOk = true;
            }
        }catch (Throwable e) {
            e.printStackTrace();
            isFloatOk = false;
            XposedBridge.log("CONTROL -----未找到PhoneWindowManager "+e);
        }
        try {
            if(nmsCls!=null){
                XC_MethodHook hook1 = new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                String apppkg = (String)methodHookParam.args[0];
                                if(!methodHookParam.args[0].equals(methodHookParam.args[1])){
                                    if("android".equals(methodHookParam.args[1])||
                                            "com.android.systemui".equals(methodHookParam.args[1])){
                                        apppkg = (String)methodHookParam.args[0];
                                    }else{
                                        apppkg = (String)methodHookParam.args[1];
                                    }
                                }
                                Notification not1 = (Notification)methodHookParam.args[6];
                                //排除系统推送通知
                                if (not1!=null&&Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                                    String gp = not1.getGroup();
                                    if(gp!=null&&!apppkg.equals(gp)){
                                        apppkg = gp;
                                    }
                                }
                                if(isAppstart&&isAutoStartNotNotify){
                                    boolean isAutoHM = appStartPrefHMs.containsKey(apppkg+"/autostart")?(Boolean)(appStartPrefHMs.get(apppkg+"/autostart")):false;
                                    if((isAutoHM&&!startRuningPkgs.contains(apppkg))){
                                        methodHookParam.setResult(null);
                                        return;
                                    }
                                }

                                if (isSkipAdOpen&&!Common.PACKAGENAME.equals(apppkg)) {
                                    if (notifySkipKeyWords.size()>0){
                                        Notification not = (Notification)methodHookParam.args[6];
                                        CharSequence title = (CharSequence) not.extras.get(Notification.EXTRA_TITLE);
                                        CharSequence text = (CharSequence) not.extras.get(Notification.EXTRA_TEXT);
                                        if (title != null && title.toString().contains("应用控制器") && !Common.PACKAGENAME.equals(apppkg)) {//title.toString().contains("可能有害")
                                            methodHookParam.setResult(null);
                                            return;
                                        } else if (text != null && text.toString().contains("应用控制器") && !Common.PACKAGENAME.equals(apppkg)) {
                                            methodHookParam.setResult(null);
                                            return;
                                        }
                                        Method cxtField = sysCls.getDeclaredMethod("getContext");
                                        cxtField.setAccessible(true);
                                        Context cxtObject = (Context)cxtField.invoke(methodHookParam.thisObject);
                                        String appName = apppkg;
                                        try {
                                            PackageManager pm = cxtObject.getPackageManager();
                                            PackageInfo packageInfo = pm.getPackageInfo(apppkg,PackageManager.GET_GIDS);
                                            if(packageInfo.applicationInfo!=null&&packageInfo.applicationInfo.loadLabel(pm)!=null){
                                                appName = packageInfo.applicationInfo.loadLabel(pm).toString();
                                            }
                                        }catch (Exception e){
                                            appName = apppkg;
                                        }
                                        for(String s:notifySkipKeyWords){
                                            if (title != null && title.toString().contains(s)) {
                                                methodHookParam.setResult(null);
                                                return;
                                            } else if (text != null && text.toString().contains(s)) {
                                                methodHookParam.setResult(null);
                                                return;
                                            }else if (apppkg != null && apppkg.toString().equals(s)) {
                                                methodHookParam.setResult(null);
                                                return;
                                            }else if (appName != null && appName.equals(s)) {
                                                methodHookParam.setResult(null);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("^^^^^^^^^^^^^^XposedStartListenerNotify notifyPosted error "+e+"^^^^^^^^^^^^^^^^^");
                        }
                    }
                };
                XposedUtil.hookMethod(nmsCls, XposedUtil.getParmsByName(nmsCls,"enqueueNotificationInternal"), "enqueueNotificationInternal",hook1);
            }
        }catch (Throwable e){
            e.printStackTrace();
        }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            try {
                final Class apperrorsCls = XposedUtil.findClass("com.android.server.am.AppErrors", lpparam.classLoader);
                if(apperrorsCls!=null){
                    XC_MethodHook hook = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                            try {
                                Object proc = methodHookParam.args[0];
                                Field infoField = proc.getClass().getDeclaredField("info");
                                infoField.setAccessible(true);
                                ApplicationInfo info = (ApplicationInfo)infoField.get(proc);
                                XposedBridge.log("CONTROL_ANR_" + info.packageName );
                                Method method = proc.getClass().getDeclaredMethod("kill",String.class,boolean.class);
                                method.setAccessible(true);
                                method.invoke(proc,"stop "+info.packageName,false);
                                methodHookParam.setResult(null);
                                return;
                            } catch (RuntimeException e) {
                                XposedBridge.log("^^^^^^^^^^^^^^appNotResponding出错 " + e + "^^^^^^^^^^^^^^^^^");
                            }
                        }
                    };
                    XposedUtil.hookMethod(apperrorsCls,XposedUtil.getParmsByName(apperrorsCls,"appNotResponding"),"appNotResponding",hook);
                }
            }catch (Throwable e){
                e.printStackTrace();
            }
        }

        /**
         *setFirewallEnabled
         *setFirewallChainEnabled
         *setFirewallChainState
         *getFirewallChainState
         *setFirewallUidRuleLocked
         */

        if(netServiceCls!=null) {
            try {
                final int FIREWALL_CHAIN_NONE = 0;
                final int FIREWALL_CHAIN_DOZABLE = 1;
                final int FIREWALL_CHAIN_STANDBY = 2;
                final int FIREWALL_CHAIN_POWERSAVE = 3;

                final String FIREWALL_CHAIN_NAME_NONE = "none";
                final String FIREWALL_CHAIN_NAME_DOZABLE = "dozable";
                final String FIREWALL_CHAIN_NAME_STANDBY = "standby";
                final String FIREWALL_CHAIN_NAME_POWERSAVE = "powersave";

                final int FIREWALL_RULE_DEFAULT = 0;
                final int FIREWALL_RULE_ALLOW = 1;
                final int FIREWALL_RULE_DENY = 2;
                XposedUtil.hookConstructorMethod(netServiceCls, new Class[]{Context.class, String.class}, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            final Object netObj = param.thisObject;
                            Context context = (Context)param.args[0];
                            final Method setFireWallMethod = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M?netServiceCls.getDeclaredMethod("setFirewallUidRule",int.class,int.class,int.class):netServiceCls.getDeclaredMethod("setFirewallUidRule",int.class,boolean.class);
                            setFireWallMethod.setAccessible(true);
                            final Method setFirewallEnabledMethod = netServiceCls.getDeclaredMethod("setFirewallEnabled",boolean.class);
                            setFirewallEnabledMethod.setAccessible(true);
                            final Method isFirewallEnabledMethod = netServiceCls.getDeclaredMethod("isFirewallEnabled");
                            isFirewallEnabledMethod.setAccessible(true);
                            BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context context, Intent intent) {
                                    try {
                                        if (!isPriOpen){
                                            return;
                                        }
                                        String action = intent.getAction();
                                        if("com.click369.control.ams.net.add".equals(action)){
                                            int uid = intent.getIntExtra("uid",-1);
                                            String type = intent.getStringExtra("type");
                                            int netType = getNetworkType(context);
                                            boolean isAdd = false;
                                            if("wifi".equals(type)){
                                                netWifiList.add(uid);
                                                isAdd = netType==2;
                                            }else if("mobile".equals(type)){
                                                netMobileList.add(uid);
                                                isAdd = netType==1;
                                            }
                                            if(isAdd){
                                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                    setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_DENY);
                                                }else{
                                                    setFireWallMethod.invoke(netObj,uid,false);
                                                }
                                            }
                                        }else if("com.click369.control.ams.net.remove".equals(action)){
                                            int uid = intent.getIntExtra("uid",-1);
                                            String type = intent.getStringExtra("type");
                                            int netType = getNetworkType(context);
                                            boolean isRemove = false;
                                            if("wifi".equals(type)){
                                                netWifiList.remove(uid);
                                                isRemove = netType==2;
                                            }else if("mobile".equals(type)){
                                                netMobileList.remove(uid);
                                                isRemove = netType==1;
                                            }
                                            if(isRemove){
                                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                    setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_ALLOW);
                                                }else{
                                                    setFireWallMethod.invoke(netObj,uid,true);
                                                }
                                            }
                                        }else if("com.click369.control.ams.net.set".equals(action)){
                                            boolean isEnable = intent.getBooleanExtra("isenable",false);
                                            setFirewallEnabledMethod.invoke(netObj,isEnable);
//                                        XposedBridge.log("CONTROL_NET_SET:"+isEnable);
                                        }else if("com.click369.control.ams.net.get".equals(action)){
                                            boolean isEnable = (Boolean) isFirewallEnabledMethod.invoke(netObj);
                                            XposedBridge.log("CONTROL_NET_ISENABLE:"+isEnable);
                                        }else if("com.click369.control.ams.net.init".equals(action)){
                                            HashSet<Integer> sets = new HashSet<Integer>();
                                            sets.addAll(netWifiList);
                                            sets.addAll(netMobileList);
                                            for(int uid:sets){
                                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                    setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_ALLOW);
                                                }else{
                                                    setFireWallMethod.invoke(netObj,uid,true);
                                                }
                                            }
                                            if(intent.hasExtra("wifilist")&&intent.hasExtra("mobilelist")){
                                                HashSet<Integer> netWifiListTemp = (HashSet<Integer>)intent.getSerializableExtra("wifilist");
                                                HashSet<Integer> netMobileListTemp = (HashSet<Integer>)intent.getSerializableExtra("mobilelist");
                                                netWifiList.clear();
                                                netMobileList.clear();
                                                netWifiList.addAll(netWifiListTemp);
                                                netMobileList.addAll(netMobileListTemp);
                                                XposedBridge.log("CONTROL_NETCONTROL_"+netWifiList.size()+" "+netMobileList.size());
                                            }
                                            int type = getNetworkType(context);
                                            if(type==1){
                                                for(int uid:netMobileList){
                                                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                        setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_DENY);
                                                    }else{
                                                        setFireWallMethod.invoke(netObj,uid,false);
                                                    }
                                                }
                                            }else if(type ==2){
                                                for(int uid:netWifiList){
                                                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                        setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_DENY);
                                                    }else{
                                                        setFireWallMethod.invoke(netObj,uid,false);
                                                    }
                                                }
                                            }
                                        }// 监听网络连接，包括wifi和移动数据的打开和关闭,以及连接上可用的连接都会接到监听
                                        else if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                                            //获取联网状态的NetworkInfo对象
                                            NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                                            if (info != null) {
                                                //如果当前的网络连接成功并且网络连接可用
                                                if (NetworkInfo.State.CONNECTED == info.getState() && info.isAvailable()) {//链接
                                                    if (info.getType() == ConnectivityManager.TYPE_WIFI ){
                                                        for(int uid:netWifiList){
                                                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                                setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_DENY);
                                                            }else{
                                                                setFireWallMethod.invoke(netObj,uid,false);
                                                            }
                                                        }
                                                    }else if(info.getType() == ConnectivityManager.TYPE_MOBILE) {
                                                        for(int uid:netMobileList){
                                                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                                setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_DENY);
                                                            }else{
                                                                setFireWallMethod.invoke(netObj,uid,false);
                                                            }
                                                        }
                                                    }
                                                } else {//断开
                                                    if (info.getType() == ConnectivityManager.TYPE_WIFI ){
                                                        for(int uid:netWifiList){
                                                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                                setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_ALLOW);
                                                            }else{
                                                                setFireWallMethod.invoke(netObj,uid,true);
                                                            }
                                                        }
                                                    }else if(info.getType() == ConnectivityManager.TYPE_MOBILE) {
                                                        for(int uid:netMobileList){
                                                            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                                                setFireWallMethod.invoke(netObj,FIREWALL_CHAIN_NONE,uid,FIREWALL_RULE_ALLOW);
                                                            }else{
                                                                setFireWallMethod.invoke(netObj,uid,true);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }catch (Exception e){
                                        e.printStackTrace();
                                    }
                                }
                            };
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction("com.click369.control.ams.net.add");
                            intentFilter.addAction("com.click369.control.ams.net.remove");
                            intentFilter.addAction("com.click369.control.ams.net.init");
                            intentFilter.addAction("com.click369.control.ams.net.set");
                            intentFilter.addAction("com.click369.control.ams.net.get");
//                        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
//                        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
                            intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
                            context.registerReceiver(broadcastReceiver,intentFilter);
//                        XposedBridge.log("CONTROL_NET_REG:"+isFirewallEnabledMethod.invoke(netObj));
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

    }

    public static int getNetworkType(Context context) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return -1;
        }
        NetworkInfo networkinfo = manager.getActiveNetworkInfo();
        if (networkinfo == null || !networkinfo.isAvailable()) {
            return -1;
        }
        if(networkinfo.getType() == ConnectivityManager.TYPE_MOBILE){
            return  1;
        }else if(networkinfo.getType() == ConnectivityManager.TYPE_WIFI){
            return  2;
        }
        return -1;
    }



//    private static boolean isProcessHasACTORSER(Object proc){
//        try {
//            Field activitiesField = proc.getClass().getDeclaredField("activities");
//            activitiesField.setAccessible(true);
//            Field servicesField = proc.getClass().getDeclaredField("services");
//            servicesField.setAccessible(true);
//            Field executingServicesField = proc.getClass().getDeclaredField("executingServices");
//            executingServicesField.setAccessible(true);
//            ArrayList activities = (ArrayList)activitiesField.get(proc);
//            ArraySet services = (ArraySet)servicesField.get(proc);
//            ArraySet executingServices = (ArraySet)executingServicesField.get(proc);
//            return activities.size()>0||services.size()>0||executingServices.size()>0;
//        }catch (Exception e){
//            e.printStackTrace();
//            return true;
//        }
//    }
}