/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.mediarouter.media;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Build;
import android.view.Display;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.mediarouter.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Provides routes for built-in system destinations such as the local display
 * and speaker.  On Jellybean and newer platform releases, queries the framework
 * MediaRouter for framework-provided routes and registers non-framework-provided
 * routes as user routes.
 */
abstract class SystemMediaRouteProvider extends MediaRouteProvider {

    public static final String PACKAGE_NAME = "android";
    public static final String DEFAULT_ROUTE_ID = "DEFAULT_ROUTE";

    protected SystemMediaRouteProvider(Context context) {
        super(context, new ProviderMetadata(new ComponentName(PACKAGE_NAME,
                SystemMediaRouteProvider.class.getName())));
    }

    public static SystemMediaRouteProvider obtain(Context context, SyncCallback syncCallback) {
        if (Build.VERSION.SDK_INT >= 24) {
            return new Api24Impl(context, syncCallback);
        }
        if (Build.VERSION.SDK_INT >= 18) {
            return new JellybeanMr2Impl(context, syncCallback);
        }
        if (Build.VERSION.SDK_INT >= 17) {
            return new JellybeanMr1Impl(context, syncCallback);
        }
        if (Build.VERSION.SDK_INT >= 16) {
            return new JellybeanImpl(context, syncCallback);
        }
        return new LegacyImpl(context);
    }

    /**
     * Called by the media router when a route is added to synchronize state with
     * the framework media router.
     */
    public void onSyncRouteAdded(MediaRouter.RouteInfo route) {
    }

    /**
     * Called by the media router when a route is removed to synchronize state with
     * the framework media router.
     */
    public void onSyncRouteRemoved(MediaRouter.RouteInfo route) {
    }

    /**
     * Called by the media router when a route is changed to synchronize state with
     * the framework media router.
     */
    public void onSyncRouteChanged(MediaRouter.RouteInfo route) {
    }

    /**
     * Called by the media router when a route is selected to synchronize state with
     * the framework media router.
     */
    public void onSyncRouteSelected(MediaRouter.RouteInfo route) {
    }

    /**
     * Callbacks into the media router to synchronize state with the framework media router.
     */
    public interface SyncCallback {
        void onSystemRouteSelectedByDescriptorId(@NonNull String id);
    }

    /**
     * Legacy implementation for platform versions prior to Jellybean.
     */
    static class LegacyImpl extends SystemMediaRouteProvider {
        static final int PLAYBACK_STREAM = AudioManager.STREAM_MUSIC;

        private static final ArrayList<IntentFilter> CONTROL_FILTERS;

        static {
            IntentFilter f = new IntentFilter();
            f.addCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO);
            f.addCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO);

            CONTROL_FILTERS = new ArrayList<IntentFilter>();
            CONTROL_FILTERS.add(f);
        }

        final AudioManager mAudioManager;
        private final VolumeChangeReceiver mVolumeChangeReceiver;
        int mLastReportedVolume = -1;

        public LegacyImpl(Context context) {
            super(context);
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            mVolumeChangeReceiver = new VolumeChangeReceiver();

            // There's no need to specify RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED here, since
            // LegacyImpl is not used on versions of Android new enough to allow this.
            context.registerReceiver(mVolumeChangeReceiver,
                    new IntentFilter(VolumeChangeReceiver.VOLUME_CHANGED_ACTION));
            publishRoutes();
        }

        void publishRoutes() {
            Resources r = getContext().getResources();
            int maxVolume = mAudioManager.getStreamMaxVolume(PLAYBACK_STREAM);
            mLastReportedVolume = mAudioManager.getStreamVolume(PLAYBACK_STREAM);
            MediaRouteDescriptor defaultRoute = new MediaRouteDescriptor.Builder(
                    DEFAULT_ROUTE_ID, r.getString(R.string.mr_system_route_name))
                    .addControlFilters(CONTROL_FILTERS)
                    .setPlaybackStream(PLAYBACK_STREAM)
                    .setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL)
                    .setVolumeHandling(MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE)
                    .setVolumeMax(maxVolume)
                    .setVolume(mLastReportedVolume)
                    .build();

            MediaRouteProviderDescriptor providerDescriptor =
                    new MediaRouteProviderDescriptor.Builder()
                            .addRoute(defaultRoute)
                            .build();
            setDescriptor(providerDescriptor);
        }

        @Override
        public RouteController onCreateRouteController(@NonNull String routeId) {
            if (routeId.equals(DEFAULT_ROUTE_ID)) {
                return new DefaultRouteController();
            }
            return null;
        }

        final class DefaultRouteController extends RouteController {
            @Override
            public void onSetVolume(int volume) {
                mAudioManager.setStreamVolume(PLAYBACK_STREAM, volume, 0);
                publishRoutes();
            }

            @Override
            public void onUpdateVolume(int delta) {
                int volume = mAudioManager.getStreamVolume(PLAYBACK_STREAM);
                int maxVolume = mAudioManager.getStreamMaxVolume(PLAYBACK_STREAM);
                int newVolume = Math.min(maxVolume, Math.max(0, volume + delta));
                if (newVolume != volume) {
                    mAudioManager.setStreamVolume(PLAYBACK_STREAM, volume, 0);
                }
                publishRoutes();
            }
        }

        final class VolumeChangeReceiver extends BroadcastReceiver {
            // These constants come from AudioManager.
            public static final String VOLUME_CHANGED_ACTION =
                    "android.media.VOLUME_CHANGED_ACTION";
            public static final String EXTRA_VOLUME_STREAM_TYPE =
                    "android.media.EXTRA_VOLUME_STREAM_TYPE";
            public static final String EXTRA_VOLUME_STREAM_VALUE =
                    "android.media.EXTRA_VOLUME_STREAM_VALUE";

            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(VOLUME_CHANGED_ACTION)) {
                    final int streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1);
                    if (streamType == PLAYBACK_STREAM) {
                        final int volume = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1);
                        if (volume >= 0 && volume != mLastReportedVolume) {
                            publishRoutes();
                        }
                    }
                }
            }
        }
    }

    /**
     * Jellybean implementation.
     */
    @RequiresApi(16)
    static class JellybeanImpl extends SystemMediaRouteProvider
            implements MediaRouterJellybean.Callback, MediaRouterJellybean.VolumeCallback {
        private static final ArrayList<IntentFilter> LIVE_AUDIO_CONTROL_FILTERS;

        static {
            IntentFilter f = new IntentFilter();
            f.addCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO);

            LIVE_AUDIO_CONTROL_FILTERS = new ArrayList<>();
            LIVE_AUDIO_CONTROL_FILTERS.add(f);
        }

        private static final ArrayList<IntentFilter> LIVE_VIDEO_CONTROL_FILTERS;

        static {
            IntentFilter f = new IntentFilter();
            f.addCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO);

            LIVE_VIDEO_CONTROL_FILTERS = new ArrayList<>();
            LIVE_VIDEO_CONTROL_FILTERS.add(f);
        }

        private final SyncCallback mSyncCallback;

        protected final android.media.MediaRouter mRouter;
        protected final android.media.MediaRouter.Callback mCallback;
        protected final android.media.MediaRouter.VolumeCallback mVolumeCallback;
        protected final android.media.MediaRouter.RouteCategory mUserRouteCategory;
        protected int mRouteTypes;
        protected boolean mActiveScan;
        protected boolean mCallbackRegistered;

        // Maintains an association from framework routes to support library routes.
        // Note that we cannot use the tag field for this because an application may
        // have published its own user routes to the framework media router and already
        // used the tag for its own purposes.
        protected final ArrayList<SystemRouteRecord> mSystemRouteRecords =
                new ArrayList<>();

        // Maintains an association from support library routes to framework routes.
        protected final ArrayList<UserRouteRecord> mUserRouteRecords =
                new ArrayList<>();

        private MediaRouterJellybean.SelectRouteWorkaround mSelectRouteWorkaround;
        private MediaRouterJellybean.GetDefaultRouteWorkaround mGetDefaultRouteWorkaround;

        public JellybeanImpl(Context context, SyncCallback syncCallback) {
            super(context);
            mSyncCallback = syncCallback;
            mRouter = MediaRouterJellybean.getMediaRouter(context);
            mCallback = createCallback();
            mVolumeCallback = createVolumeCallback();

            Resources r = context.getResources();
            mUserRouteCategory = MediaRouterJellybean.createRouteCategory(
                    mRouter, r.getString(R.string.mr_user_route_category_name), false);

            updateSystemRoutes();
        }

        @Override
        public RouteController onCreateRouteController(@NonNull String routeId) {
            int index = findSystemRouteRecordByDescriptorId(routeId);
            if (index >= 0) {
                SystemRouteRecord record = mSystemRouteRecords.get(index);
                return new SystemRouteController(record.mRoute);
            }
            return null;
        }

        @Override
        public void onDiscoveryRequestChanged(MediaRouteDiscoveryRequest request) {
            int newRouteTypes = 0;
            boolean newActiveScan = false;
            if (request != null) {
                final MediaRouteSelector selector = request.getSelector();
                final List<String> categories = selector.getControlCategories();
                final int count = categories.size();
                for (int i = 0; i < count; i++) {
                    String category = categories.get(i);
                    if (category.equals(MediaControlIntent.CATEGORY_LIVE_AUDIO)) {
                        newRouteTypes |= MediaRouterJellybean.ROUTE_TYPE_LIVE_AUDIO;
                    } else if (category.equals(MediaControlIntent.CATEGORY_LIVE_VIDEO)) {
                        newRouteTypes |= MediaRouterJellybean.ROUTE_TYPE_LIVE_VIDEO;
                    } else {
                        newRouteTypes |= MediaRouterJellybean.ROUTE_TYPE_USER;
                    }
                }
                newActiveScan = request.isActiveScan();
            }

            if (mRouteTypes != newRouteTypes || mActiveScan != newActiveScan) {
                mRouteTypes = newRouteTypes;
                mActiveScan = newActiveScan;
                updateSystemRoutes();
            }
        }

        @Override
        public void onRouteAdded(@NonNull android.media.MediaRouter.RouteInfo route) {
            if (addSystemRouteNoPublish(route)) {
                publishRoutes();
            }
        }

        private void updateSystemRoutes() {
            updateCallback();
            boolean changed = false;
            for (android.media.MediaRouter.RouteInfo route : MediaRouterJellybean.getRoutes(
                    mRouter)) {
                changed |= addSystemRouteNoPublish(route);
            }
            if (changed) {
                publishRoutes();
            }
        }

        private boolean addSystemRouteNoPublish(android.media.MediaRouter.RouteInfo route) {
            if (getUserRouteRecord(route) == null && findSystemRouteRecord(route) < 0) {
                String id = assignRouteId(route);
                SystemRouteRecord record = new SystemRouteRecord(route, id);
                updateSystemRouteDescriptor(record);
                mSystemRouteRecords.add(record);
                return true;
            }
            return false;
        }

        private String assignRouteId(android.media.MediaRouter.RouteInfo route) {
            // TODO: The framework media router should supply a unique route id that
            // we can use here.  For now we use a hash of the route name and take care
            // to dedupe it.
            boolean isDefault = (getDefaultRoute() == route);
            String id = isDefault ? DEFAULT_ROUTE_ID :
                    String.format(Locale.US, "ROUTE_%08x", getRouteName(route).hashCode());
            if (findSystemRouteRecordByDescriptorId(id) < 0) {
                return id;
            }
            for (int i = 2; ; i++) {
                String newId = String.format(Locale.US, "%s_%d", id, i);
                if (findSystemRouteRecordByDescriptorId(newId) < 0) {
                    return newId;
                }
            }
        }

        @Override
        public void onRouteRemoved(@NonNull android.media.MediaRouter.RouteInfo route) {
            if (getUserRouteRecord(route) == null) {
                int index = findSystemRouteRecord(route);
                if (index >= 0) {
                    mSystemRouteRecords.remove(index);
                    publishRoutes();
                }
            }
        }

        @Override
        public void onRouteChanged(@NonNull android.media.MediaRouter.RouteInfo route) {
            if (getUserRouteRecord(route) == null) {
                int index = findSystemRouteRecord(route);
                if (index >= 0) {
                    SystemRouteRecord record = mSystemRouteRecords.get(index);
                    updateSystemRouteDescriptor(record);
                    publishRoutes();
                }
            }
        }

        @Override
        public void onRouteVolumeChanged(@NonNull android.media.MediaRouter.RouteInfo route) {
            if (getUserRouteRecord(route) == null) {
                int index = findSystemRouteRecord(route);
                if (index >= 0) {
                    SystemRouteRecord record = mSystemRouteRecords.get(index);
                    int newVolume = MediaRouterJellybean.RouteInfo.getVolume(route);
                    if (newVolume != record.mRouteDescriptor.getVolume()) {
                        record.mRouteDescriptor =
                                new MediaRouteDescriptor.Builder(record.mRouteDescriptor)
                                        .setVolume(newVolume)
                                        .build();
                        publishRoutes();
                    }
                }
            }
        }

        @Override
        public void onRouteSelected(int type,
                @NonNull android.media.MediaRouter.RouteInfo route) {
            if (route != MediaRouterJellybean.getSelectedRoute(mRouter,
                    MediaRouterJellybean.ALL_ROUTE_TYPES)) {
                // The currently selected route has already changed so this callback
                // is stale.  Drop it to prevent getting into sync loops.
                return;
            }

            UserRouteRecord userRouteRecord = getUserRouteRecord(route);
            if (userRouteRecord != null) {
                userRouteRecord.mRoute.select();
            } else {
                // Select the route if it already exists in the compat media router.
                // If not, we will select it instead when the route is added.
                int index = findSystemRouteRecord(route);
                if (index >= 0) {
                    SystemRouteRecord record = mSystemRouteRecords.get(index);
                    mSyncCallback.onSystemRouteSelectedByDescriptorId(record.mRouteDescriptorId);
                }
            }
        }

        @Override
        public void onRouteUnselected(int type,
                @NonNull android.media.MediaRouter.RouteInfo route) {
            // Nothing to do when a route is unselected.
            // We only need to handle when a route is selected.
        }

        @Override
        public void onRouteGrouped(@NonNull android.media.MediaRouter.RouteInfo route,
                @NonNull android.media.MediaRouter.RouteGroup group, int index) {
            // Route grouping is deprecated and no longer supported.
        }

        @Override
        public void onRouteUngrouped(@NonNull android.media.MediaRouter.RouteInfo route,
                @NonNull android.media.MediaRouter.RouteGroup group) {
            // Route grouping is deprecated and no longer supported.
        }

        @Override
        public void onVolumeSetRequest(@NonNull android.media.MediaRouter.RouteInfo route,
                int volume) {
            UserRouteRecord record = getUserRouteRecord(route);
            if (record != null) {
                record.mRoute.requestSetVolume(volume);
            }
        }

        @Override
        public void onVolumeUpdateRequest(@NonNull android.media.MediaRouter.RouteInfo route,
                int direction) {
            UserRouteRecord record = getUserRouteRecord(route);
            if (record != null) {
                record.mRoute.requestUpdateVolume(direction);
            }
        }

        @Override
        public void onSyncRouteAdded(MediaRouter.RouteInfo route) {
            if (route.getProviderInstance() != this) {
                android.media.MediaRouter.UserRouteInfo userRoute =
                        MediaRouterJellybean.createUserRoute(mRouter, mUserRouteCategory);
                UserRouteRecord record = new UserRouteRecord(route, userRoute);
                MediaRouterJellybean.RouteInfo.setTag(userRoute, record);
                MediaRouterJellybean.UserRouteInfo.setVolumeCallback(userRoute, mVolumeCallback);
                updateUserRouteProperties(record);
                mUserRouteRecords.add(record);
                MediaRouterJellybean.addUserRoute(mRouter, userRoute);
            } else {
                // If the newly added route is the counterpart of the currently selected
                // route in the framework media router then ensure it is selected in
                // the compat media router.
                android.media.MediaRouter.RouteInfo routeObj =
                        MediaRouterJellybean.getSelectedRoute(
                                mRouter, MediaRouterJellybean.ALL_ROUTE_TYPES);
                int index = findSystemRouteRecord(routeObj);
                if (index >= 0) {
                    SystemRouteRecord record = mSystemRouteRecords.get(index);
                    if (record.mRouteDescriptorId.equals(route.getDescriptorId())) {
                        route.select();
                    }
                }
            }
        }

        @Override
        public void onSyncRouteRemoved(MediaRouter.RouteInfo route) {
            if (route.getProviderInstance() != this) {
                int index = findUserRouteRecord(route);
                if (index >= 0) {
                    UserRouteRecord record = mUserRouteRecords.remove(index);
                    MediaRouterJellybean.RouteInfo.setTag(record.mUserRoute, null);
                    MediaRouterJellybean.UserRouteInfo.setVolumeCallback(record.mUserRoute, null);
                    MediaRouterJellybean.removeUserRoute(mRouter, record.mUserRoute);
                }
            }
        }

        @Override
        public void onSyncRouteChanged(MediaRouter.RouteInfo route) {
            if (route.getProviderInstance() != this) {
                int index = findUserRouteRecord(route);
                if (index >= 0) {
                    UserRouteRecord record = mUserRouteRecords.get(index);
                    updateUserRouteProperties(record);
                }
            }
        }

        @Override
        public void onSyncRouteSelected(MediaRouter.RouteInfo route) {
            if (!route.isSelected()) {
                // The currently selected route has already changed so this callback
                // is stale.  Drop it to prevent getting into sync loops.
                return;
            }

            if (route.getProviderInstance() != this) {
                int index = findUserRouteRecord(route);
                if (index >= 0) {
                    UserRouteRecord record = mUserRouteRecords.get(index);
                    selectRoute(record.mUserRoute);
                }
            } else {
                int index = findSystemRouteRecordByDescriptorId(route.getDescriptorId());
                if (index >= 0) {
                    SystemRouteRecord record = mSystemRouteRecords.get(index);
                    selectRoute(record.mRoute);
                }
            }
        }

        protected void publishRoutes() {
            MediaRouteProviderDescriptor.Builder builder =
                    new MediaRouteProviderDescriptor.Builder();
            int count = mSystemRouteRecords.size();
            for (int i = 0; i < count; i++) {
                builder.addRoute(mSystemRouteRecords.get(i).mRouteDescriptor);
            }

            setDescriptor(builder.build());
        }

        protected int findSystemRouteRecord(android.media.MediaRouter.RouteInfo route) {
            final int count = mSystemRouteRecords.size();
            for (int i = 0; i < count; i++) {
                if (mSystemRouteRecords.get(i).mRoute == route) {
                    return i;
                }
            }
            return -1;
        }

        protected int findSystemRouteRecordByDescriptorId(String id) {
            final int count = mSystemRouteRecords.size();
            for (int i = 0; i < count; i++) {
                if (mSystemRouteRecords.get(i).mRouteDescriptorId.equals(id)) {
                    return i;
                }
            }
            return -1;
        }

        protected int findUserRouteRecord(MediaRouter.RouteInfo route) {
            final int count = mUserRouteRecords.size();
            for (int i = 0; i < count; i++) {
                if (mUserRouteRecords.get(i).mRoute == route) {
                    return i;
                }
            }
            return -1;
        }

        protected UserRouteRecord getUserRouteRecord(android.media.MediaRouter.RouteInfo route) {
            Object tag = MediaRouterJellybean.RouteInfo.getTag(route);
            return tag instanceof UserRouteRecord ? (UserRouteRecord) tag : null;
        }

        protected void updateSystemRouteDescriptor(SystemRouteRecord record) {
            // We must always recreate the route descriptor when making any changes
            // because they are intended to be immutable once published.
            MediaRouteDescriptor.Builder builder = new MediaRouteDescriptor.Builder(
                    record.mRouteDescriptorId, getRouteName(record.mRoute));
            onBuildSystemRouteDescriptor(record, builder);
            record.mRouteDescriptor = builder.build();
        }

        protected String getRouteName(android.media.MediaRouter.RouteInfo route) {
            // Routes should not have null names but it may happen for badly configured
            // user routes.  We tolerate this by using an empty name string here but
            // such unnamed routes will be discarded by the media router upstream
            // (with a log message so we can track down the problem).
            CharSequence name = MediaRouterJellybean.RouteInfo.getName(route, getContext());
            return name != null ? name.toString() : "";
        }

        protected void onBuildSystemRouteDescriptor(SystemRouteRecord record,
                MediaRouteDescriptor.Builder builder) {
            int supportedTypes = MediaRouterJellybean.RouteInfo.getSupportedTypes(
                    record.mRoute);
            if ((supportedTypes & MediaRouterJellybean.ROUTE_TYPE_LIVE_AUDIO) != 0) {
                builder.addControlFilters(LIVE_AUDIO_CONTROL_FILTERS);
            }
            if ((supportedTypes & MediaRouterJellybean.ROUTE_TYPE_LIVE_VIDEO) != 0) {
                builder.addControlFilters(LIVE_VIDEO_CONTROL_FILTERS);
            }

            builder.setPlaybackType(
                    MediaRouterJellybean.RouteInfo.getPlaybackType(record.mRoute));
            builder.setPlaybackStream(
                    MediaRouterJellybean.RouteInfo.getPlaybackStream(record.mRoute));
            builder.setVolume(
                    MediaRouterJellybean.RouteInfo.getVolume(record.mRoute));
            builder.setVolumeMax(
                    MediaRouterJellybean.RouteInfo.getVolumeMax(record.mRoute));
            builder.setVolumeHandling(
                    MediaRouterJellybean.RouteInfo.getVolumeHandling(record.mRoute));
        }

        protected void updateUserRouteProperties(UserRouteRecord record) {
            MediaRouterJellybean.UserRouteInfo.setName(
                    record.mUserRoute, record.mRoute.getName());
            MediaRouterJellybean.UserRouteInfo.setPlaybackType(
                    record.mUserRoute, record.mRoute.getPlaybackType());
            MediaRouterJellybean.UserRouteInfo.setPlaybackStream(
                    record.mUserRoute, record.mRoute.getPlaybackStream());
            MediaRouterJellybean.UserRouteInfo.setVolume(
                    record.mUserRoute, record.mRoute.getVolume());
            MediaRouterJellybean.UserRouteInfo.setVolumeMax(
                    record.mUserRoute, record.mRoute.getVolumeMax());
            MediaRouterJellybean.UserRouteInfo.setVolumeHandling(
                    record.mUserRoute, record.mRoute.getVolumeHandling());
        }

        protected void updateCallback() {
            if (mCallbackRegistered) {
                mCallbackRegistered = false;
                MediaRouterJellybean.removeCallback(mRouter, mCallback);
            }

            if (mRouteTypes != 0) {
                mCallbackRegistered = true;
                MediaRouterJellybean.addCallback(mRouter, mRouteTypes, mCallback);
            }
        }

        protected android.media.MediaRouter.Callback createCallback() {
            return MediaRouterJellybean.createCallback(this);
        }

        protected android.media.MediaRouter.VolumeCallback createVolumeCallback() {
            return MediaRouterJellybean.createVolumeCallback(this);
        }

        protected void selectRoute(android.media.MediaRouter.RouteInfo route) {
            if (mSelectRouteWorkaround == null) {
                mSelectRouteWorkaround = new MediaRouterJellybean.SelectRouteWorkaround();
            }
            mSelectRouteWorkaround.selectRoute(mRouter,
                    MediaRouterJellybean.ALL_ROUTE_TYPES, route);
        }

        protected Object getDefaultRoute() {
            if (mGetDefaultRouteWorkaround == null) {
                mGetDefaultRouteWorkaround = new MediaRouterJellybean.GetDefaultRouteWorkaround();
            }
            return mGetDefaultRouteWorkaround.getDefaultRoute(mRouter);
        }

        /**
         * Represents a route that is provided by the framework media router
         * and published by this route provider to the support library media router.
         */
        protected static final class SystemRouteRecord {
            public final android.media.MediaRouter.RouteInfo mRoute;
            public final String mRouteDescriptorId;
            public MediaRouteDescriptor mRouteDescriptor; // assigned immediately after creation

            public SystemRouteRecord(android.media.MediaRouter.RouteInfo route, String id) {
                mRoute = route;
                mRouteDescriptorId = id;
            }
        }

        /**
         * Represents a route that is provided by the support library media router
         * and published by this route provider to the framework media router.
         */
        protected static final class UserRouteRecord {
            public final MediaRouter.RouteInfo mRoute;
            public final android.media.MediaRouter.UserRouteInfo mUserRoute;

            public UserRouteRecord(MediaRouter.RouteInfo route,
                    android.media.MediaRouter.UserRouteInfo userRoute) {
                mRoute = route;
                mUserRoute = userRoute;
            }
        }

        protected static final class SystemRouteController extends RouteController {
            private final android.media.MediaRouter.RouteInfo mRoute;

            public SystemRouteController(android.media.MediaRouter.RouteInfo route) {
                mRoute = route;
            }

            @Override
            public void onSetVolume(int volume) {
                MediaRouterJellybean.RouteInfo.requestSetVolume(mRoute, volume);
            }

            @Override
            public void onUpdateVolume(int delta) {
                MediaRouterJellybean.RouteInfo.requestUpdateVolume(mRoute, delta);
            }
        }
    }

    /**
     * Jellybean MR1 implementation.
     */
    @RequiresApi(17)
    private static class JellybeanMr1Impl extends JellybeanImpl
            implements MediaRouterJellybeanMr1.Callback {
        private MediaRouterJellybeanMr1.ActiveScanWorkaround mActiveScanWorkaround;
        private MediaRouterJellybeanMr1.IsConnectingWorkaround mIsConnectingWorkaround;

        public JellybeanMr1Impl(Context context, SyncCallback syncCallback) {
            super(context, syncCallback);
        }

        @Override
        public void onRoutePresentationDisplayChanged(
                @NonNull android.media.MediaRouter.RouteInfo route) {
            int index = findSystemRouteRecord(route);
            if (index >= 0) {
                SystemRouteRecord record = mSystemRouteRecords.get(index);
                Display newPresentationDisplay =
                        MediaRouterJellybeanMr1.RouteInfo.getPresentationDisplay(route);
                int newPresentationDisplayId = (newPresentationDisplay != null
                        ? newPresentationDisplay.getDisplayId() : -1);
                if (newPresentationDisplayId
                        != record.mRouteDescriptor.getPresentationDisplayId()) {
                    record.mRouteDescriptor =
                            new MediaRouteDescriptor.Builder(record.mRouteDescriptor)
                                    .setPresentationDisplayId(newPresentationDisplayId)
                                    .build();
                    publishRoutes();
                }
            }
        }

        @Override
        protected void onBuildSystemRouteDescriptor(SystemRouteRecord record,
                MediaRouteDescriptor.Builder builder) {
            super.onBuildSystemRouteDescriptor(record, builder);

            if (!MediaRouterJellybeanMr1.RouteInfo.isEnabled(record.mRoute)) {
                builder.setEnabled(false);
            }

            if (isConnecting(record)) {
                builder.setConnectionState(MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING);
            }

            Display presentationDisplay =
                    MediaRouterJellybeanMr1.RouteInfo.getPresentationDisplay(record.mRoute);
            if (presentationDisplay != null) {
                builder.setPresentationDisplayId(presentationDisplay.getDisplayId());
            }
        }

        @Override
        protected void updateCallback() {
            super.updateCallback();

            if (mActiveScanWorkaround == null) {
                mActiveScanWorkaround = new MediaRouterJellybeanMr1.ActiveScanWorkaround(
                        getContext(), getHandler());
            }
            mActiveScanWorkaround.setActiveScanRouteTypes(mActiveScan ? mRouteTypes : 0);
        }

        @Override
        protected android.media.MediaRouter.Callback createCallback() {
            return MediaRouterJellybeanMr1.createCallback(this);
        }

        protected boolean isConnecting(SystemRouteRecord record) {
            if (mIsConnectingWorkaround == null) {
                mIsConnectingWorkaround = new MediaRouterJellybeanMr1.IsConnectingWorkaround();
            }
            return mIsConnectingWorkaround.isConnecting(record.mRoute);
        }
    }

    /**
     * Jellybean MR2 implementation.
     */
    @RequiresApi(18)
    private static class JellybeanMr2Impl extends JellybeanMr1Impl {
        public JellybeanMr2Impl(Context context, SyncCallback syncCallback) {
            super(context, syncCallback);
        }

        @DoNotInline
        @Override
        protected void onBuildSystemRouteDescriptor(SystemRouteRecord record,
                MediaRouteDescriptor.Builder builder) {
            super.onBuildSystemRouteDescriptor(record, builder);

            CharSequence description = record.mRoute.getDescription();
            if (description != null) {
                builder.setDescription(description.toString());
            }
        }

        @DoNotInline
        @Override
        protected void selectRoute(android.media.MediaRouter.RouteInfo route) {
            MediaRouterJellybean.selectRoute(mRouter, MediaRouterJellybean.ALL_ROUTE_TYPES, route);
        }

        @DoNotInline
        @Override
        protected android.media.MediaRouter.RouteInfo getDefaultRoute() {
            return mRouter.getDefaultRoute();
        }

        @DoNotInline
        @Override
        protected void updateUserRouteProperties(UserRouteRecord record) {
            super.updateUserRouteProperties(record);
            record.mUserRoute.setDescription(record.mRoute.getDescription());
        }

        @DoNotInline
        @Override
        protected void updateCallback() {
            if (mCallbackRegistered) {
                MediaRouterJellybean.removeCallback(mRouter, mCallback);
            }

            mCallbackRegistered = true;
            int flags = MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS
                    | (mActiveScan ? MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN : 0);
            mRouter.addCallback(mRouteTypes, mCallback, flags);
        }

        @DoNotInline
        @Override
        protected boolean isConnecting(SystemRouteRecord record) {
            return record.mRoute.isConnecting();
        }
    }

    /**
     * Api24 implementation.
     */
    @RequiresApi(24)
    private static class Api24Impl extends JellybeanMr2Impl {
        public Api24Impl(Context context, SyncCallback syncCallback) {
            super(context, syncCallback);
        }

        @SuppressLint("WrongConstant") // False positive. See b/283059575.
        @Override
        @DoNotInline
        protected void onBuildSystemRouteDescriptor(SystemRouteRecord record,
                MediaRouteDescriptor.Builder builder) {
            super.onBuildSystemRouteDescriptor(record, builder);
            builder.setDeviceType(record.mRoute.getDeviceType());
        }
    }
}
