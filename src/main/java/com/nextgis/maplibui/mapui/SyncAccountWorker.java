package com.nextgis.maplibui.mapui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.hypertrack.hyperlog.HyperLog;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.util.AccountUtil;
import com.nextgis.maplib.util.Constants;

import java.util.concurrent.TimeUnit;

public class SyncAccountWorker extends Worker {
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_PERIOD_SECONDS = "period_seconds";
    private static final String WORK_NAME_PREFIX = "ngw-account-sync-";

    public SyncAccountWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context, String accountName, long periodSeconds) {
        scheduleInternal(context, accountName, periodSeconds, Math.max(1L, periodSeconds));
    }

    public static void scheduleSoon(Context context, String accountName, long periodSeconds) {
        scheduleInternal(context, accountName, periodSeconds, 1L);
    }

    public static void cancel(Context context, String accountName) {
        if (context == null || TextUtils.isEmpty(accountName)) {
            Log.d("SSYNC", "SyncAccountWorker.cancel skipped account=" + accountName);
            return;
        }

        String workName = workNameForAccount(accountName);
        WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(workName);
        Log.d("SSYNC", "SyncAccountWorker.cancel account=" + accountName + " work=" + workName);
        HyperLog.v(Constants.TAG, "SyncAccountWorker.cancel account=" + accountName + " work=" + workName);
    }

    private static void scheduleInternal(
            Context context,
            String accountName,
            long periodSeconds,
            long delaySeconds) {
        if (context == null || TextUtils.isEmpty(accountName)) {
            Log.e("SSYNC", "SyncAccountWorker.schedule skipped: bad context/account account=" + accountName);
            return;
        }
        if (periodSeconds <= 0) {
            Log.d("SSYNC", "SyncAccountWorker.schedule cancel for non-positive period account="
                    + accountName + " period=" + periodSeconds);
            cancel(context, accountName);
            return;
        }

        long safePeriod = periodSeconds;
        long safeDelay = delaySeconds > 0 ? delaySeconds : safePeriod;
        Context appContext = context.getApplicationContext();

        Data data = new Data.Builder()
                .putString(KEY_ACCOUNT_NAME, accountName)
                .putLong(KEY_PERIOD_SECONDS, safePeriod)
                .build();

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncAccountWorker.class)
                .setConstraints(constraints)
                .setInputData(data)
                .setInitialDelay(safeDelay, TimeUnit.SECONDS)
                .addTag(WORK_NAME_PREFIX)
                .addTag(workNameForAccount(accountName))
                .build();

        String workName = workNameForAccount(accountName);
        WorkManager.getInstance(appContext)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request);

        Log.d("SSYNC", "SyncAccountWorker.schedule account=" + accountName
                + " period=" + safePeriod + " delay=" + safeDelay + " work=" + workName);
        HyperLog.v(Constants.TAG, "SyncAccountWorker.schedule account=" + accountName
                + " period=" + safePeriod + " delay=" + safeDelay + " work=" + workName);
    }

    @NonNull
    @Override
    public Result doWork() {
        String accountName = getInputData().getString(KEY_ACCOUNT_NAME);
        long periodSeconds = getInputData().getLong(KEY_PERIOD_SECONDS, Constants.DEFAULT_SYNC_PERIOD);
        Context context = getApplicationContext();

        Log.d("SSYNC", "SyncAccountWorker.doWork start account=" + accountName
                + " period=" + periodSeconds);

        if (TextUtils.isEmpty(accountName)) {
            Log.e("SSYNC", "SyncAccountWorker.doWork failed: account name is empty");
            return Result.failure();
        }

        if (!(context instanceof IGISApplication)) {
            Log.e("SSYNC", "SyncAccountWorker.doWork failed: application is not IGISApplication");
            return Result.failure();
        }

        IGISApplication application = (IGISApplication) context;
        Account account = findAccount(context, application, accountName);
        if (account == null) {
            Log.e("SSYNC", "SyncAccountWorker.doWork account not found: " + accountName
                    + " type=" + application.getAccountsType());
            HyperLog.w(Constants.TAG, "SyncAccountWorker.doWork account not found: " + accountName);
            return Result.success();
        }

        String authority = application.getAuthority();
        try {
            int isSyncable = ContentResolver.getIsSyncable(account, authority);
            if (isSyncable <= 0) {
                ContentResolver.setIsSyncable(account, authority, 1);
                Log.d("SSYNC", "SyncAccountWorker.doWork fixed syncable account=" + accountName
                        + " old=" + isSyncable + " authority=" + authority);
            }

            if (!ContentResolver.getSyncAutomatically(account, authority)) {
                Log.d("SSYNC", "SyncAccountWorker.doWork skip: auto sync is off account=" + accountName);
                HyperLog.v(Constants.TAG, "SyncAccountWorker.doWork auto sync off account=" + accountName);
                return Result.success();
            }

            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, false);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, false);

            Log.d("SSYNC", "SyncAccountWorker.doWork requestSync account=" + accountName
                    + " authority=" + authority + " extras=" + extras);
            ContentResolver.requestSync(account, authority, extras);

            long nextPeriod = AccountUtil.getSyncPeriodForAccount(context, accountName, periodSeconds);
            schedule(context, accountName, nextPeriod);
            return Result.success();
        } catch (SecurityException e) {
            Log.e("SSYNC", "SyncAccountWorker.doWork security error account=" + accountName
                    + ": " + e.getMessage(), e);
            HyperLog.e(Constants.TAG, "SyncAccountWorker security error account=" + accountName, e);
            return Result.failure();
        } catch (RuntimeException e) {
            Log.e("SSYNC", "SyncAccountWorker.doWork failed account=" + accountName
                    + ": " + e.getMessage(), e);
            HyperLog.e(Constants.TAG, "SyncAccountWorker failed account=" + accountName, e);
            return Result.retry();
        }
    }

    private static Account findAccount(
            Context context,
            IGISApplication application,
            String accountName) {
        AccountManager accountManager = AccountManager.get(context);
        for (Account account : accountManager.getAccountsByType(application.getAccountsType())) {
            if (accountName.equals(account.name)) {
                return account;
            }
        }
        return null;
    }

    private static String workNameForAccount(String accountName) {
        return WORK_NAME_PREFIX + Integer.toHexString(accountName.hashCode());
    }
}
