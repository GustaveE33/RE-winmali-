package com.winlator.cmod;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.VelocityTracker;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ConsoleHomeFragment extends Fragment {
    private static final String PREFERENCES_NAME = "console_home_state";
    private static final String KEY_LAST_HOME_APP_ID = "last_home_app_id";
    private static final int DEFAULT_HOME_APP_ID = R.id.main_menu_containers;
    private int lastSelectedHomeAppId;

    private final List<HomeApp> apps = new ArrayList<>();
    private FrameLayout homeRoot;
    private ImageView heroArt;
    private TextView heroTitle;
    private TextView heroDescription;
    private RecyclerView appRail;
    private LinearLayoutManager railLayoutManager;
    private LinearSnapHelper snapHelper;
    private AppsAdapter adapter;
    private VelocityTracker swipeVelocityTracker;
    private int selectedPosition = -1;
    static final int SHORTCUTS_BACKGROUND_COLOR = 0xFF2369B5;
    static final int CONTAINERS_BACKGROUND_COLOR = 0xFF59459B;
    static final int INPUT_BACKGROUND_COLOR = 0xFF147E8A;
    static final int CONTENTS_BACKGROUND_COLOR = 0xFFA56B27;
    static final int COMMUNITY_BACKGROUND_COLOR = 0xFF9A4267;
    static final int FILE_MANAGER_BACKGROUND_COLOR = 0xFF28734F;
    static final int WRAPPERS_BACKGROUND_COLOR = 0xFF73802F;
    static final int PANVK_BACKGROUND_COLOR = 0xFF2D7894;
    static final int SETTINGS_BACKGROUND_COLOR = 0xFF385FAD;
    static final int ABOUT_BACKGROUND_COLOR = 0xFF435269;
    private int currentBackgroundColor = SHORTCUTS_BACKGROUND_COLOR;
    private ValueAnimator backgroundAnimator;
    private boolean isInitializingRail = true;
    private float swipeStartCoordinate;
    private float swipeLastCoordinate;
    private boolean swipeMoved;
    private boolean isLaunchingApp;
    private int swipePointerId = MotionEvent.INVALID_POINTER_ID;
    private int swipeTouchSlop;
    private int swipeMinimumFlingVelocity;
    private int swipeMaximumFlingVelocity;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apps.add(new HomeApp(R.string.shortcuts, R.string.home_shortcuts_description, R.drawable.ic_home_shortcuts, R.id.main_menu_shortcuts, SHORTCUTS_BACKGROUND_COLOR));
        apps.add(new HomeApp(R.string.containers, R.string.home_containers_description, R.drawable.ic_home_containers, R.id.main_menu_containers, CONTAINERS_BACKGROUND_COLOR));
        apps.add(new HomeApp(R.string.home_input_devices, R.string.home_input_devices_description, R.drawable.icon_input_controls, R.id.main_menu_input_controls, INPUT_BACKGROUND_COLOR));
        apps.add(new HomeApp(R.string.contents, R.string.home_contents_description, R.drawable.ic_home_contents, R.id.main_menu_contents, CONTENTS_BACKGROUND_COLOR));
        apps.add(new HomeApp(R.string.community_configs, R.string.home_community_configs_description, R.drawable.ic_community_configs, R.id.main_menu_community_configs, COMMUNITY_BACKGROUND_COLOR));
        apps.add(new HomeApp(R.string.file_manager, R.string.home_file_manager_description, R.drawable.icon_menu_file_manager, R.id.main_menu_file_manager, FILE_MANAGER_BACKGROUND_COLOR));
        apps.add(new HomeApp(R.string.home_graphics_drivers_short, R.string.home_graphics_drivers_description, R.drawable.ic_graphics_management, R.id.main_menu_manage_graphics_drivers, WRAPPERS_BACKGROUND_COLOR, R.string.manage_graphics_drivers));
        apps.add(new HomeApp(R.string.mali_panvk_drivers, R.string.home_panvk_drivers_description,
                R.drawable.ic_home_panvk, R.id.main_menu_adrenotools_gpu_drivers, PANVK_BACKGROUND_COLOR));
        apps.add(new HomeApp(R.string.settings, R.string.home_settings_description, R.drawable.icon_settings, R.id.main_menu_settings, SETTINGS_BACKGROUND_COLOR));
        apps.add(new HomeApp(R.string.about, R.string.home_about_description, R.drawable.icon_about, R.id.main_menu_about, ABOUT_BACKGROUND_COLOR));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.console_home_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        homeRoot = view.findViewById(R.id.FLConsoleHome);
        heroArt = view.findViewById(R.id.IVConsoleHeroArt);
        heroTitle = view.findViewById(R.id.TVConsoleHeroTitle);
        heroDescription = view.findViewById(R.id.TVConsoleHeroDescription);

        appRail = view.findViewById(R.id.RVConsoleApps);
        boolean verticalCarousel = isVerticalCarousel();
        int railOrientation = verticalCarousel ? RecyclerView.VERTICAL : RecyclerView.HORIZONTAL;
        railLayoutManager = new LinearLayoutManager(requireContext(), railOrientation, false);
        snapHelper = new LinearSnapHelper();
        ViewConfiguration viewConfiguration = ViewConfiguration.get(requireContext());
        swipeTouchSlop = viewConfiguration.getScaledTouchSlop();
        swipeMinimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
        swipeMaximumFlingVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
        appRail.setLayoutManager(railLayoutManager);
        snapHelper.attachToRecyclerView(appRail);
        appRail.setNestedScrollingEnabled(false);
        appRail.setHorizontalScrollBarEnabled(false);
        appRail.setVerticalScrollBarEnabled(false);
        appRail.setHasFixedSize(true);
        appRail.setClipChildren(false);
        appRail.setClipToPadding(false);
        appRail.setItemAnimator(null);
        adapter = new AppsAdapter(appRail);
        appRail.setAdapter(adapter);
        appRail.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!isLaunchingApp) updateVisibleTileDepth();
                if (isInitializingRail || isLaunchingApp) return;
                View centeredChild = findCenteredChild();
                if (centeredChild == null) return;
                int position = recyclerView.getChildAdapterPosition(centeredChild);
                if (position != RecyclerView.NO_POSITION && position != selectedPosition) {
                    if (selectedPosition >= 0 && recyclerView.isInTouchMode()) {
                        UiSoundEffects.playNavigation(requireContext());
                    }
                    showSelectedApp(position, false);
                }
            }
        });

        View swipeArea = view.findViewById(R.id.VConsoleSwipeArea);
        swipeArea.setClickable(true);
        swipeArea.setOnTouchListener(this::handleSwipe);
        lastSelectedHomeAppId = getSavedHomeAppId();
        int initialPosition = findAppPosition(lastSelectedHomeAppId);
        if (initialPosition == RecyclerView.NO_POSITION) {
            initialPosition = findAppPosition(DEFAULT_HOME_APP_ID);
            lastSelectedHomeAppId = DEFAULT_HOME_APP_ID;
        }
        final int homePosition = initialPosition;
        appRail.post(() -> {
            if (adapter == null || appRail == null) return;
            int width;
            if (verticalCarousel) {
                width = Math.max(dp(96), appRail.getWidth() - dp(10));
                int edgePadding = Math.max(0, (appRail.getHeight() - dp(118)) / 2);
                appRail.setPadding(appRail.getPaddingLeft(), edgePadding,
                        appRail.getPaddingRight(), edgePadding);
            } else {
                width = Math.max(dp(110), Math.min(dp(154), appRail.getWidth() / 5));
                int edgePadding = Math.max(0, (appRail.getWidth() - width - dp(10)) / 2);
                appRail.setPadding(edgePadding, appRail.getPaddingTop(),
                        edgePadding, appRail.getPaddingBottom());
            }
            adapter.setTileWidth(width);
            railLayoutManager.scrollToPositionWithOffset(homePosition, 0);
            isInitializingRail = false;
            updateVisibleTileDepth();
            appRail.post(this::animateEntrance);
        });
        showSelectedApp(homePosition, false);
    }

    private int findAppPosition(int menuItemId) {
        for (int i = 0; i < apps.size(); i++) {
            if (apps.get(i).menuItemId == menuItemId) return i;
        }
        return RecyclerView.NO_POSITION;
    }

    private boolean isVerticalCarousel() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private int getSavedHomeAppId() {
        if (!isAdded()) return DEFAULT_HOME_APP_ID;
        return requireContext().getSharedPreferences(PREFERENCES_NAME, 0)
                .getInt(KEY_LAST_HOME_APP_ID, DEFAULT_HOME_APP_ID);
    }

    private void saveHomeAppId(int menuItemId) {
        if (!isAdded()) return;
        requireContext().getSharedPreferences(PREFERENCES_NAME, 0)
                .edit()
                .putInt(KEY_LAST_HOME_APP_ID, menuItemId)
                .apply();
    }

    private void animateEntrance() {
        if (appRail == null || isLaunchingApp) return;
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            if (!activity.isStartupAnimationEnabled()) return;
            if (activity.scheduleStartupHomeEntrance(this::animateEntrance)) return;
        }
        int firstPosition = Integer.MAX_VALUE;
        int lastPosition = RecyclerView.NO_POSITION;
        for (int i = 0; i < appRail.getChildCount(); i++) {
            int position = appRail.getChildAdapterPosition(appRail.getChildAt(i));
            if (position == RecyclerView.NO_POSITION) continue;
            firstPosition = Math.min(firstPosition, position);
            lastPosition = Math.max(lastPosition, position);
        }
        if (lastPosition == RecyclerView.NO_POSITION) return;

        for (int offset = 0; firstPosition + offset <= lastPosition - offset; offset++) {
            animateEntranceTile(firstPosition + offset, offset * 115L);
            int rightPosition = lastPosition - offset;
            if (rightPosition != firstPosition + offset) {
                animateEntranceTile(rightPosition, offset * 115L);
            }
        }
    }

    private void animateEntranceTile(int position, long delay) {
        RecyclerView.ViewHolder holder = appRail.findViewHolderForAdapterPosition(position);
        if (holder == null) return;
        View tile = holder.itemView;
        tile.animate().cancel();
        tile.setAlpha(0f);
        tile.setTranslationY(-dp(180));

        ObjectAnimator drop = ObjectAnimator.ofFloat(tile, View.TRANSLATION_Y, -dp(180), dp(18));
        drop.setDuration(390);
        drop.setInterpolator(new DecelerateInterpolator(1.1f));
        ObjectAnimator settle = ObjectAnimator.ofFloat(tile, View.TRANSLATION_Y, dp(18), 0f);
        settle.setDuration(300);
        settle.setInterpolator(new OvershootInterpolator(0.85f));
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(tile, View.ALPHA, 0f, 1f);
        fadeIn.setDuration(240);
        fadeIn.setInterpolator(new DecelerateInterpolator());

        AnimatorSet dropAndFade = new AnimatorSet();
        dropAndFade.playTogether(drop, fadeIn);
        AnimatorSet tileEntrance = new AnimatorSet();
        tileEntrance.playSequentially(dropAndFade, settle);
        tileEntrance.setStartDelay(delay);
        tileEntrance.start();
    }

    private void beginAppLaunch(int position) {
        if (isLaunchingApp || position < 0 || position >= apps.size() || appRail == null) return;
        UiSoundEffects.playMenuClosed();
        isLaunchingApp = true;
        appRail.stopScroll();
        showSelectedApp(position, true);
        awaitSelectedTileCentered(position, SystemClock.uptimeMillis());
    }

    private void awaitSelectedTileCentered(int position, long startTime) {
        if (appRail == null || !isAdded()) return;
        View snappedView = snapHelper == null || railLayoutManager == null
                ? null : snapHelper.findSnapView(railLayoutManager);
        int snappedPosition = snappedView == null
                ? RecyclerView.NO_POSITION : appRail.getChildAdapterPosition(snappedView);
        boolean centered = snappedPosition == position
                && appRail.getScrollState() == RecyclerView.SCROLL_STATE_IDLE;
        if (centered || SystemClock.uptimeMillis() - startTime >= 1200) {
            showSelectedApp(position, false);
            animateNeighborsThenLaunch(position);
            return;
        }
        appRail.postDelayed(() -> awaitSelectedTileCentered(position, startTime), 16);
    }

    private void animateNeighborsThenLaunch(int selected) {
        if (appRail == null) return;
        List<View> visibleTiles = new ArrayList<>();
        int farthestDistance = 0;
        for (int i = 0; i < appRail.getChildCount(); i++) {
            View tile = appRail.getChildAt(i);
            int position = appRail.getChildAdapterPosition(tile);
            if (position == RecyclerView.NO_POSITION) continue;
            visibleTiles.add(tile);
            farthestDistance = Math.max(farthestDistance, Math.abs(position - selected));
        }

        List<Animator> staggeredExits = new ArrayList<>();
        for (View tile : visibleTiles) {
            int position = appRail.getChildAdapterPosition(tile);
            Animator exit = createNeighborExit(tile);
            exit.setStartDelay((farthestDistance - Math.abs(position - selected)) * 70L);
            staggeredExits.add(exit);
        }

        AnimatorSet neighbors = new AnimatorSet();
        neighbors.playTogether(staggeredExits);
        neighbors.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (appRail != null && isAdded()) openLaunchedApp(selected);
            }
        });
        if (staggeredExits.isEmpty()) {
            openLaunchedApp(selected);
        } else {
            neighbors.start();
        }
    }

    private Animator createNeighborExit(View tile) {
        if (tile instanceof ViewGroup) ((ViewGroup) tile).setClipChildren(false);

        float startY = tile.getTranslationY();
        float liftY = startY - dp(12);
        ObjectAnimator lift = ObjectAnimator.ofFloat(tile, View.TRANSLATION_Y, startY, liftY);
        lift.setDuration(100);
        lift.setInterpolator(new PathInterpolator(0.2f, 0f, 0.3f, 1f));

        ObjectAnimator driftUp = ObjectAnimator.ofFloat(tile, View.TRANSLATION_Y, liftY, -appRail.getHeight() - dp(110));
        driftUp.setDuration(300);
        driftUp.setInterpolator(new PathInterpolator(0.22f, 0f, 0.2f, 1f));
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(tile, View.ALPHA, tile.getAlpha(), 0f);
        fadeOut.setDuration(260);
        fadeOut.setInterpolator(new PathInterpolator(0.22f, 0f, 0.2f, 1f));
        ObjectAnimator liftScaleX = ObjectAnimator.ofFloat(tile, View.SCALE_X, tile.getScaleX(), 1.08f);
        liftScaleX.setDuration(300);
        liftScaleX.setInterpolator(new PathInterpolator(0.22f, 0f, 0.2f, 1f));
        ObjectAnimator liftScaleY = ObjectAnimator.ofFloat(tile, View.SCALE_Y, tile.getScaleY(), 1.08f);
        liftScaleY.setDuration(300);
        liftScaleY.setInterpolator(new PathInterpolator(0.22f, 0f, 0.2f, 1f));

        AnimatorSet upwardAndFade = new AnimatorSet();
        upwardAndFade.playTogether(driftUp, fadeOut, liftScaleX, liftScaleY);
        AnimatorSet liftThenRise = new AnimatorSet();
        liftThenRise.playSequentially(lift, upwardAndFade);
        return liftThenRise;
    }

    private void openLaunchedApp(int position) {
        if (!isAdded()) return;
        int menuItemId = apps.get(position).menuItemId;
        ((MainActivity) requireActivity()).openNavigationItem(menuItemId);
        if (menuItemId == R.id.main_menu_about && appRail != null) {
            appRail.postDelayed(this::restoreRailAfterAbout, 650);
        }
    }

    private void restoreRailAfterAbout() {
        if (appRail == null) return;
        for (int i = 0; i < appRail.getChildCount(); i++) {
            View tile = appRail.getChildAt(i);
            int position = appRail.getChildAdapterPosition(tile);
            if (position == RecyclerView.NO_POSITION) continue;
            ImageView icon = tile.findViewById(R.id.IVConsoleAppIcon);
            tile.animate().cancel();
            tile.setAlpha(1f);
            tile.setTranslationY(0f);
            tile.setScaleX(1f);
            tile.setScaleY(1f);
            tile.setElevation(0f);
            icon.setTranslationX(0f);
            icon.setTranslationY(0f);
            float scale = position == selectedPosition ? 1.22f : 1f;
            icon.setScaleX(scale);
            icon.setScaleY(scale);
            tile.findViewById(R.id.TVConsoleAppTitle).setAlpha(1f);
            icon.setAlpha(1f);
        }
        heroTitle.setAlpha(1f);
        heroDescription.setAlpha(1f);
        isLaunchingApp = false;
    }

    private View findCenteredChild() {
        if (appRail == null) return null;
        if (snapHelper != null && railLayoutManager != null) {
            View snapped = snapHelper.findSnapView(railLayoutManager);
            if (snapped != null) return snapped;
        }
        boolean vertical = isVerticalCarousel();
        int center = vertical ? appRail.getHeight() / 2 : appRail.getWidth() / 2;
        View closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < appRail.getChildCount(); i++) {
            View child = appRail.getChildAt(i);
            int childCenter = vertical
                    ? (child.getTop() + child.getBottom()) / 2
                    : (child.getLeft() + child.getRight()) / 2;
            int distance = Math.abs(center - childCenter);
            if (distance < closestDistance) {
                closest = child;
                closestDistance = distance;
            }
        }
        return closest;
    }

    private void updateVisibleTileDepth() {
        if (appRail == null) return;
        boolean vertical = isVerticalCarousel();
        float center = vertical ? appRail.getHeight() / 2f : appRail.getWidth() / 2f;
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < appRail.getChildCount(); i++) {
            View tile = appRail.getChildAt(i);
            int position = appRail.getChildAdapterPosition(tile);
            boolean selected = position == selectedPosition;
            float tileCenter = vertical
                    ? (tile.getTop() + tile.getBottom()) / 2f
                    : (tile.getLeft() + tile.getRight()) / 2f;
            float dimension = Math.max(1, vertical ? tile.getHeight() : tile.getWidth());
            float distance = Math.min(1f, Math.abs(center - tileCenter) / (dimension * 1.15f));
            float scale = 1.34f - (0.56f * distance);
            tile.setScaleX(scale);
            tile.setScaleY(scale);
            tile.setAlpha(1f - (0.48f * distance));
            tile.setTranslationX(vertical ? 3f * density * distance : 0f);
            tile.setTranslationY(vertical ? 0f : 3f * density * distance);
            tile.setElevation(12f * density * (1f - distance));
            tile.setSelected(distance < 0.08f);
            tile.setPivotX(tile.getWidth() / 2f);
            tile.setPivotY(tile.getHeight() / 2f);
            ImageView icon = tile.findViewById(R.id.IVConsoleAppIcon);
            if (icon != null) {
                float iconScale = selected ? 1.16f : 1f;
                icon.setScaleX(iconScale);
                icon.setScaleY(iconScale);
            }
        }
    }

    private boolean handleSwipe(View view, MotionEvent event) {
        if (appRail == null) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                recycleSwipeVelocityTracker();
                swipeVelocityTracker = VelocityTracker.obtain();
                swipeVelocityTracker.addMovement(event);
                swipePointerId = event.getPointerId(0);
                swipeStartCoordinate = swipeCoordinate(event, 0);
                swipeLastCoordinate = swipeStartCoordinate;
                swipeMoved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (swipeVelocityTracker != null) swipeVelocityTracker.addMovement(event);
                int pointerIndex = event.findPointerIndex(swipePointerId);
                if (pointerIndex < 0) return true;
                float coordinate = swipeCoordinate(event, pointerIndex);
                float distance = swipeMoved
                        ? swipeLastCoordinate - coordinate
                        : swipeStartCoordinate - coordinate;
                if (swipeMoved || Math.abs(coordinate - swipeStartCoordinate) > swipeTouchSlop) {
                    swipeMoved = true;
                    if (isVerticalCarousel()) appRail.scrollBy(0, Math.round(distance));
                    else appRail.scrollBy(Math.round(distance), 0);
                }
                swipeLastCoordinate = coordinate;
                return true;
            case MotionEvent.ACTION_UP:
                if (swipeVelocityTracker != null) {
                    swipeVelocityTracker.addMovement(event);
                    swipeVelocityTracker.computeCurrentVelocity(1000, swipeMaximumFlingVelocity);
                }
                boolean flung = false;
                if (swipeMoved && swipeVelocityTracker != null) {
                    float velocity = isVerticalCarousel()
                            ? swipeVelocityTracker.getYVelocity(swipePointerId)
                            : swipeVelocityTracker.getXVelocity(swipePointerId);
                    if (Math.abs(velocity) >= swipeMinimumFlingVelocity) {
                        flung = isVerticalCarousel()
                                ? appRail.fling(0, Math.round(-velocity))
                                : appRail.fling(Math.round(-velocity), 0);
                    }
                }
                if (!flung) centerNearestApp();
                recycleSwipeVelocityTracker();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (swipeMoved) centerNearestApp();
                recycleSwipeVelocityTracker();
                return true;
            default:
                if (swipeVelocityTracker != null) swipeVelocityTracker.addMovement(event);
                return true;
        }
    }

    private void centerNearestApp() {
        View centeredChild = findCenteredChild();
        if (centeredChild == null || appRail == null) return;
        int position = appRail.getChildAdapterPosition(centeredChild);
        if (position != RecyclerView.NO_POSITION) scrollToPositionCentered(position);
    }

    private float swipeCoordinate(MotionEvent event, int pointerIndex) {
        return isVerticalCarousel() ? event.getY(pointerIndex) : event.getX(pointerIndex);
    }

    private void recycleSwipeVelocityTracker() {
        if (swipeVelocityTracker != null) {
            swipeVelocityTracker.recycle();
            swipeVelocityTracker = null;
        }
        swipePointerId = MotionEvent.INVALID_POINTER_ID;
    }

    private void showSelectedApp(int position, boolean centerRail) {
        if (position < 0 || position >= apps.size() || homeRoot == null) return;
        if (position == selectedPosition) {
            if (centerRail) scrollToPositionCentered(position);
            return;
        }

        lastSelectedHomeAppId = apps.get(position).menuItemId;
        saveHomeAppId(lastSelectedHomeAppId);
        selectedPosition = position;
        HomeApp app = apps.get(position);
        animateBackground(app.backgroundColor);

        heroArt.animate().cancel();
        heroArt.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(110).withEndAction(() -> {
            if (heroArt == null || !isAdded()) return;
            heroArt.setImageResource(app.icon);
            heroArt.animate().alpha(0.12f).scaleX(1f).scaleY(1f)
                    .setDuration(280)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }).start();
        heroArt.setColorFilter(Color.WHITE);
        int title = app.featuredTitle == 0 ? app.title : app.featuredTitle;
        animateTextChange(heroTitle, getString(title));
        animateTextChange(heroDescription, getString(app.description));
        if (adapter != null) adapter.setSelectedPosition(position, centerRail);
    }

    private void animateTextChange(TextView textView, String value) {
        textView.animate().cancel();
        textView.animate().alpha(0f).setDuration(100).withEndAction(() -> {
            if (textView.getParent() == null) return;
            textView.setText(value);
            textView.animate().alpha(1f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    private void animateBackground(int color) {
        if (backgroundAnimator != null) backgroundAnimator.cancel();
        backgroundAnimator = ValueAnimator.ofArgb(currentBackgroundColor, color);
        backgroundAnimator.setDuration(380);
        backgroundAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        backgroundAnimator.addUpdateListener(animation -> {
            if (homeRoot != null) homeRoot.setBackground(createBackground((int) animation.getAnimatedValue()));
        });
        backgroundAnimator.start();
        currentBackgroundColor = color;
    }

    static GradientDrawable createBackground(int color) {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color, blend(color, Color.BLACK, 0.58f), 0xFF070B13});
    }

    private static int blend(int color, int other, float ratio) {
        float inverseRatio = 1.0f - ratio;
        return Color.rgb(
                Math.round(Color.red(color) * inverseRatio + Color.red(other) * ratio),
                Math.round(Color.green(color) * inverseRatio + Color.green(other) * ratio),
                Math.round(Color.blue(color) * inverseRatio + Color.blue(other) * ratio));
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void scrollToPositionCentered(int position) {
        if (appRail == null || railLayoutManager == null) return;
        LinearSmoothScroller scroller = new LinearSmoothScroller(requireContext()) {
            @Override
            protected float calculateSpeedPerPixel(android.util.DisplayMetrics displayMetrics) {
                return 110f / displayMetrics.densityDpi;
            }

            @Override
            protected int getHorizontalSnapPreference() {
                return SNAP_TO_ANY;
            }

            @Override
            protected int getVerticalSnapPreference() {
                return SNAP_TO_ANY;
            }

            @Override
            public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                return (boxStart + boxEnd) / 2 - (viewStart + viewEnd) / 2;
            }
        };
        scroller.setTargetPosition(position);
        railLayoutManager.startSmoothScroll(scroller);
    }

    @Override
    public void onDestroyView() {
        if (backgroundAnimator != null) {
            backgroundAnimator.cancel();
            backgroundAnimator = null;
        }
        homeRoot = null;
        heroArt = null;
        heroTitle = null;
        heroDescription = null;
        appRail = null;
        railLayoutManager = null;
        snapHelper = null;
        recycleSwipeVelocityTracker();
        adapter = null;
        super.onDestroyView();
    }

    private final class AppsAdapter extends RecyclerView.Adapter<AppViewHolder> {
        private final RecyclerView appRail;
        private int tileWidth;
        private int highlightedPosition = -1;

        AppsAdapter(RecyclerView appRail) {
            this.appRail = appRail;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return apps.get(position).menuItemId;
        }

        void setTileWidth(int width) {
            if (width <= 0 || width == tileWidth) return;
            tileWidth = width;
            notifyDataSetChanged();
        }

        void setSelectedPosition(int position, boolean centerRail) {
            highlightedPosition = position;
            updateVisibleTileDepth();
            if (centerRail) scrollToPositionCentered(position);
        }

        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext()).inflate(R.layout.console_home_app_item, parent, false);
            return new AppViewHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            HomeApp app = apps.get(position);
            String title = getString(app.title);
            holder.title.setText(title);
            holder.icon.setImageResource(app.icon);
            holder.itemView.setContentDescription(getString(app.featuredTitle == 0 ? app.title : app.featuredTitle) + ", " + getString(app.description));
            holder.itemView.setSelected(position == highlightedPosition);
            if (holder.itemView instanceof ViewGroup) {
                ((ViewGroup) holder.itemView).setClipChildren(false);
            }
            if (getActivity() instanceof MainActivity) {
                MainActivity activity = (MainActivity) getActivity();
                if (activity.isStartupLoadingVisible() && activity.isStartupAnimationEnabled()) {
                    holder.itemView.setAlpha(0f);
                    holder.itemView.setTranslationY(-dp(180));
                } else {
                    holder.itemView.setAlpha(1f);
                    holder.itemView.setTranslationY(0f);
                }
            }

            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
            if (tileWidth > 0 && params.width != tileWidth) {
                params.width = tileWidth;
                holder.itemView.setLayoutParams(params);
            }
            holder.icon.setScaleX(1f);
            holder.icon.setScaleY(1f);

            holder.itemView.setOnFocusChangeListener((focusedView, hasFocus) -> {
                int adapterPosition = holder.getBindingAdapterPosition();
                if (hasFocus && adapterPosition != RecyclerView.NO_POSITION) {
                    boolean changed = adapterPosition != selectedPosition;
                    showSelectedApp(adapterPosition, true);
                    if (changed) UiSoundEffects.playNavigation(requireContext());
                }
            });
            holder.itemView.setOnClickListener(v -> {
                int selected = holder.getBindingAdapterPosition();
                if (selected == RecyclerView.NO_POSITION || !isAdded()) return;
                beginAppLaunch(selected);
            });
        }

        @Override
        public void onViewAttachedToWindow(@NonNull AppViewHolder holder) {
            super.onViewAttachedToWindow(holder);
            updateVisibleTileDepth();
        }

        @Override
        public void onViewRecycled(@NonNull AppViewHolder holder) {
            holder.icon.animate().cancel();
            holder.itemView.animate().cancel();
            super.onViewRecycled(holder);
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }

    private static final class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;

        AppViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.IVConsoleAppIcon);
            title = itemView.findViewById(R.id.TVConsoleAppTitle);
        }
    }

    private static final class HomeApp {
        final int title;
        final int description;
        final int icon;
        final int menuItemId;
        final int backgroundColor;
        final int featuredTitle;

        HomeApp(int title, int description, int icon, int menuItemId, int backgroundColor) {
            this(title, description, icon, menuItemId, backgroundColor, 0);
        }

        HomeApp(int title, int description, int icon, int menuItemId, int backgroundColor, int featuredTitle) {
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.menuItemId = menuItemId;
            this.backgroundColor = backgroundColor;
            this.featuredTitle = featuredTitle;
        }
    }
}
