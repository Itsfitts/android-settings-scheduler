/**
 * This code has been modified from:
 * • https://github.com/SimpleAppProjects/SimpleWear licensed under Apache License 2.0 (https://github.com/SimpleAppProjects/SimpleWear/blob/master/LICENSE.txt)
 * • https://github.com/MuntashirAkon/AppManager
 */

package android.content.pm;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.NotNull;

public interface IPackageManager extends IInterface {
    /**
     * @deprecated Deprecated since API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void grantRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 30 (Android R)
     */
    @Deprecated
    @RequiresApi(Build.VERSION_CODES.M)
    void revokeRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException;

    /**
     * @deprecated Removed in API 23 (Android M)
     */
    @Deprecated
    void grantPermission(String packageName, String permissionName) throws RemoteException;

    abstract class Stub extends Binder implements IPackageManager {

        public static IPackageManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}