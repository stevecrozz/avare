/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com)
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.ds.avare.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatSpinner;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import com.ds.avare.ChartsDownloadActivity;
import com.ds.avare.MainActivity;
import com.ds.avare.MessageActivity;
import com.ds.avare.PrefActivity;
import com.ds.avare.R;
import com.ds.avare.WebActivity;
import com.ds.avare.adapters.PopoutAdapter;
import com.ds.avare.animation.AnimateButton;
import com.ds.avare.animation.TwoButton;
import com.ds.avare.animation.TwoButton.TwoClickListener;
import com.ds.avare.flight.FlightStatusInterface;
import com.ds.avare.gps.Gps;
import com.ds.avare.gps.GpsParams;
import com.ds.avare.instruments.FuelTimer;
import com.ds.avare.instruments.UpTimer;
import com.ds.avare.place.Airport;
import com.ds.avare.place.Boundaries;
import com.ds.avare.place.Destination;
import com.ds.avare.place.Plan;
import com.ds.avare.storage.Preferences;
import com.ds.avare.storage.StringPreference;
import com.ds.avare.touch.GestureInterface;
import com.ds.avare.touch.LongTouchDestination;
import com.ds.avare.utils.GenericCallback;
import com.ds.avare.utils.Helper;
import com.ds.avare.utils.InfoLines.InfoLineFieldLoc;
import com.ds.avare.utils.NetworkHelper;
import com.ds.avare.utils.OptionButton;
import com.ds.avare.utils.Tips;
import com.ds.avare.utils.ToolbarVisibilityListener;
import com.ds.avare.views.LocationView;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

/**
 * @author zkhan, jlmcgraw
 * Main activity
 */
public class LocationFragment extends StorageServiceGpsListenerFragment implements Observer, ToolbarVisibilityListener {

    public static final String TAG = "LocationFragment";

    public static final String[] LAYER_TYPES = new String[] { "No Layer", "METAR", "NEXRAD" };

    /**
     * This view display location on the map.
     */
    private LocationView mLocationView;
    /**
     * Current destination info
     */
    private Destination mDestination;

    private Location mInitLocation;

    /**
     * Shows warning message about Avare
     */
    private AlertDialog mAlertDialogDatabase;

    /**
     * Shows exit dialog
     */
    private AlertDialog mAlertDialogExit;

    /**
     * Shows warning about GPS
     */
    private AlertDialog mGpsWarnDialog;

    /**
     * Version related warnings
     */
    private AlertDialog mWarnDialog;

    private Button mDestButton;
    private Button mCenterButton;
    private Button mDrawClearButton;
    private TwoButton mTracksButton;
    private Button mHelpButton;
    private Button mCrossButton;
    private Button mPrefButton;
    private Button mPlanButton;
    private Button mPlatesButton;
    private Button mAfdButton;
    private Button mDownloadButton;
    private Button mMenuButton;
    private Button mDrawerButton;
    private RelativeLayout mDestLayout;
    private TwoButton mSimButton;
    private TwoButton mDrawButton;
    private Button mWebButton;
    private OptionButton mChartOption;
    private OptionButton mLayerOption;
    private Bundle mExtras;
    private boolean mIsWaypoint;
    private AnimateButton mAnimateTracks;
    private AnimateButton mAnimateSim;
    private AnimateButton mAnimateWeb;
    private AnimateButton mAnimateTrack;
    private AnimateButton mAnimateChart;
    private AnimateButton mAnimateHelp;
    private AnimateButton mAnimateDownload;
    private AnimateButton mAnimatePref;
    private String mAirportPressed;

    private Button mPlanPrev;
    private ImageButton mPlanPause;
    private Button mPlanNext;

    private com.ds.avare.touch.Constants.TouchMode mTouchMode = com.ds.avare.touch.Constants.TouchMode.PAN_MODE;

    private ExpandableListView mListPopout;

    private TankObserver mTankObserver;
    private TimerObserver mTimerObserver;

    private FlightStatusInterface mFSInfc = new FlightStatusInterface() {
        @Override
        public void rollout() {
            if(mPref != null && mService != null) {
                if(mPref.shouldAutoDisplayAirportDiagram()) {
                    int nearestNum = mService.getArea().getAirportsNumber();
                    if(nearestNum > 0) {
                        /*
                         * Find the nearest airport and load its plate on rollout
                         */
                        Airport nearest = mService.getArea().getAirport(0);
                        if(nearest != null && PlatesFragment.doesAirportHaveAirportDiagram(mPref.mapsFolder(),
                                nearest.getId()) && nearest.getDistance() < Preferences.DISTANCE_TO_AUTO_LOAD_PLATE) {
                            mService.setLastPlateAirport(nearest.getId());
                            mService.setLastPlateIndex(0);
                            ((MainActivity) getContext()).showPlatesView();
                        }
                    }
                }
            }
        }
    };

    /**
     *
     * @param dest
     * @return
     */
    private boolean isSameDest(String dest) {
        if(mService != null) {
            Destination cdest = mService.getDestination();
            if(cdest != null) {
                if(dest.contains("&")) {
                    /*
                     * GPS dest needs comparison with closeness.
                     */
                    String tokens[] = dest.split("&");
                    double lon;
                    double lat;
                    try {
                        lon = Double.parseDouble(tokens[1]);
                        lat = Double.parseDouble(tokens[0]);
                    }
                    catch(Exception e) {
                        return false;
                    }
                    if(Helper.isSameGPSLocation(cdest.getLocation().getLongitude(),
                            cdest.getLocation().getLatitude(), lon, lat)) {
                        return true;
                    }
                }
                else if(dest.equals(cdest.getID())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     *
     * @param dst
     */
    private void goTo(String dst, String type) {
        mIsWaypoint = false;
        mDestination = new Destination(dst, type, mPref, mService);
        mDestination.addObserver(LocationFragment.this);
        showSnackbar(getString(R.string.Searching) + " " + dst, Snackbar.LENGTH_SHORT);
        mDestination.find();
        mDestLayout.setVisibility(View.INVISIBLE);
    }

    /**
     *
     * @param dst
     */
    private void planTo(String dst, String type) {
        mIsWaypoint = true;
        mDestination = new Destination(dst, type, mPref, mService);
        mDestination.addObserver(LocationFragment.this);
        showSnackbar(getString(R.string.Searching) + " " + dst, Snackbar.LENGTH_SHORT);
        mDestination.find();
        mDestLayout.setVisibility(View.INVISIBLE);
    }

    public void onBackPressed() {
        /*
         * Back button hides some controls
         */
        if(mDestLayout.getVisibility() == View.VISIBLE) {
            mDestLayout.setVisibility(View.INVISIBLE);
            return;
        }

        /*
         * And may exit
         */
        mAlertDialogExit = new AlertDialog.Builder(getContext()).create();
        mAlertDialogExit.setTitle(getString(R.string.Exit));
        mAlertDialogExit.setCanceledOnTouchOutside(true);
        mAlertDialogExit.setCancelable(true);
        mAlertDialogExit.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.Yes), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                /*
                 * Go to background
                 */
                setTrackState(false);   // ensure tracks are turned off
                getActivity().finish();
                dialog.dismiss();
            }
        });
        mAlertDialogExit.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.No), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                /*
                 * Go to background
                 */
                dialog.dismiss();
            }
        });

        mAlertDialogExit.show();
    }

    private void hideMenu() {
        mAnimateTracks.animateBack();
        mAnimateWeb.animateBack();
        mAnimateSim.animateBack();
        mAnimateTrack.animateBack();
        mAnimateChart.animateBack();
        mAnimateHelp.animateBack();
        mAnimateDownload.animateBack();
        mAnimatePref.animateBack();
    }

    private void showMenu() {
        mAnimateTracks.animate();
        mAnimateWeb.animate();
        mAnimateSim.animate();
        mAnimateTrack.animate();
        mAnimateChart.animate();
        mAnimateHelp.animate();
        mAnimateDownload.animate();
        mAnimatePref.animate();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("foo", "bar");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        /*
         * Throw this in case GPS is disabled.
         */
        if (Gps.isGpsDisabled(getContext(), mPref)) {
            mGpsWarnDialog = new AlertDialog.Builder(getContext()).create();
            mGpsWarnDialog.setTitle(getString(R.string.GPSEnable));
            mGpsWarnDialog.setCancelable(false);
            mGpsWarnDialog.setCanceledOnTouchOutside(false);
            mGpsWarnDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.Yes), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    Intent i = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(i);
                }
            });
            mGpsWarnDialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.No), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            mGpsWarnDialog.show();
        }

        if(mPref.showTips()) {
            mWarnDialog = new AlertDialog.Builder(getContext()).create();
            mWarnDialog.setTitle(getString(R.string.Tip));
            mWarnDialog.setMessage(Tips.getTip(getContext(), mPref));
            mWarnDialog.setCancelable(false);
            mWarnDialog.setCanceledOnTouchOutside(false);
            mWarnDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.OK), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            mWarnDialog.show();
        }

        /*
         * Check if this was sent from Google Maps
         */
        mExtras = getActivity().getIntent().getExtras();

        // Allocate the object that will get told about the status of the fuel tank
        mTankObserver = new TankObserver();
        mTimerObserver = new TimerObserver();

        mInitLocation = Gps.getLastLocation(getContext());
        if (null == mInitLocation) {
            mInitLocation = mPref.getLastLocation();
        }

        ((MainActivity) getActivity()).addToolbarVisibilityListener(TAG, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.location, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mLocationView = (LocationView) view.findViewById(R.id.location);

        /*
         * To be notified of some action in the view
         */
        mLocationView.setGestureCallback(new GestureInterface() {
            InfoLineFieldLoc _InfoLineFieldLoc;
            int _nNewSelection = 0;

            // This is the doubletap gesture that is called when the user desires
            // to change the "instrument" text display. We are passed the row and field index
            // of what is requested to change.
            @Override
            public void gestureCallBack(int nEvent, InfoLineFieldLoc infoLineFieldLoc) {
                if (infoLineFieldLoc == null) {
                    return;
                }

                _InfoLineFieldLoc = infoLineFieldLoc;

                if (GestureInterface.LONG_PRESS == nEvent) {
                    if (mService != null) {
                        mService.getInfoLines().longPress(_InfoLineFieldLoc);
                        return;
                    }
                }

                if (GestureInterface.TOUCH == nEvent) {
                    if (mService != null) {
                        mService.getInfoLines().touch(_InfoLineFieldLoc);
                        return;
                    }
                }

                if (GestureInterface.DOUBLE_TAP == nEvent) {

                    // Create the alert dialog and add the title.
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(R.string.SelectTextFieldTitle);

                    // The list of items to chose from. When a selection is made, save it off locally
                    builder.setSingleChoiceItems(_InfoLineFieldLoc.getOptions(),
                            _InfoLineFieldLoc.getSelected(),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    _nNewSelection = which;
                                }
                            });

                    // OK button, copy the new selection to the true array so it will be displayed
                    builder.setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            if (mService != null) {
                                mService.getInfoLines().setFieldType(_InfoLineFieldLoc, _nNewSelection);
                            }
                        }
                    });

                    // Cancel, nothing to do here, let the dialog self-destruct
                    builder.setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });

                    // Create and show the dialog now
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            }

            @Override
            public void gestureCallBack(int event, LongTouchDestination data) {

                if (GestureInterface.TOUCH == event) {
                    hideMenu();
                }

                if (GestureInterface.LONG_PRESS == event) {
                    /*
                     * Show the popout
                     */
                    mAirportPressed = data.airport;
                    if (mAirportPressed.contains("&")) {
                        mPlatesButton.setEnabled(false);
                        mAfdButton.setEnabled(false);
                    } else {
                        mPlatesButton.setEnabled(true);
                        mAfdButton.setEnabled(true);
                    }
                    mCrossButton.setText(data.airport + "\n" + data.info);
                    mDestLayout.setVisibility(View.VISIBLE);

                    // This allows unsetting the destination that is same as current
                    if (isSameDest(data.airport)) {
                        mDestButton.setText(getString(R.string.Delete));
                    } else {
                        mDestButton.setText(getString(R.string.ShortDestination));
                    }

                    /*
                     * Now populate the pop out weather etc.
                     */
                    PopoutAdapter p = new PopoutAdapter(getContext(), data);
                    mListPopout.setAdapter(p);
                }
            }

        });

        /*
         * black pop out
         */
        mListPopout = (ExpandableListView) view.findViewById(R.id.location_list_popout);


        mChartOption = (OptionButton)view.findViewById(R.id.location_button_maps);
        mChartOption.setCallback(new GenericCallback() {
            @Override
            public Object callback(Object o, Object o1) {
                mPref.setChartType("" + (int) o1);
                getActivity().supportInvalidateOptionsMenu();
                mLocationView.forceReload();
                return null;
            }
        });
        mChartOption.setOptions(Boundaries.getChartTypes());
        mChartOption.setCurrentSelectionIndex(Integer.parseInt(mPref.getChartType()));
        mLocationView.forceReload();

        mCenterButton = (Button) view.findViewById(R.id.location_button_center);
        mCenterButton.getBackground().setAlpha(255);
        mCenterButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mLocationView.center();
            }
        });
        mCenterButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // long press on center button sets track toggle
                mPref.setTrackUp(!mPref.isTrackUp());
                String snackbarText;
                if(mPref.isTrackUp()) {
                    mCenterButton.getBackground().setColorFilter(0xFF00FF00, PorterDuff.Mode.MULTIPLY);
                    snackbarText = getString(R.string.TrackUp);
                }
                else {
                    mCenterButton.getBackground().setColorFilter(0xFF444444, PorterDuff.Mode.MULTIPLY);
                    snackbarText = getString(R.string.NorthUp);
                }
                showSnackbar(snackbarText, Snackbar.LENGTH_SHORT);
                mLocationView.invalidate();
                return true;
            }
        });

        mCrossButton = (Button) view.findViewById(R.id.location_button_cross);
        mCrossButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                mDestLayout.setVisibility(View.INVISIBLE);
            }

        });

        mDestLayout = (RelativeLayout) view.findViewById(R.id.location_popout_layout);

        mMenuButton = (Button) view.findViewById(R.id.location_button_menu);
        mMenuButton.getBackground().setAlpha(255);
        mMenuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu();
            }
        });

        mDrawerButton = (Button) view.findViewById(R.id.location_button_drawer);
        mDrawerButton.getBackground().setAlpha(255);
        mDrawerButton.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((MainActivity) getActivity()).getDrawerLayout().openDrawer(Gravity.LEFT);
                    }
                }
        );

        mHelpButton = (Button) view.findViewById(R.id.location_button_help);
        mHelpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getContext(), WebActivity.class);
                intent.putExtra("url", NetworkHelper.getHelpUrl(getContext()));
                startActivity(intent);
            }
        });

        mLayerOption = (OptionButton) view.findViewById(R.id.location_button_layers);
        mLayerOption.setCallback(new GenericCallback() {
            @Override
            public Object callback(Object o, Object o1) {
                mPref.setLayerType(mLayerOption.getCurrentValue());
                getActivity().supportInvalidateOptionsMenu();
                mLocationView.setLayerType(mPref.getLayerType());
                return null;
            }
        });
        ArrayList<String> layerItems = new ArrayList<>(3);
        layerItems.addAll(Arrays.asList(LAYER_TYPES));
        mLayerOption.setOptions(layerItems);
        mLayerOption.setSelectedValue(mPref.getLayerType());
        mLocationView.setLayerType(mPref.getLayerType());

        mPlatesButton = (Button) view.findViewById(R.id.location_button_plate);
        mPlatesButton.getBackground().setAlpha(255);
        mPlatesButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mAirportPressed) {
                    if (mService != null) {
                        mService.setLastPlateAirport(mAirportPressed);
                        mService.setLastPlateIndex(0);
                        mDestLayout.setVisibility(View.INVISIBLE);
                        ((MainActivity) getContext()).showPlatesView();
                    }
                    mAirportPressed = null;
                }
            }
        });

        mAfdButton = (Button) view.findViewById(R.id.location_button_afd);
        mAfdButton.getBackground().setAlpha(255);
        mAfdButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(null != mAirportPressed) {
                    if(mService != null) {
                        mService.setLastAfdAirport(mAirportPressed);
                        mDestLayout.setVisibility(View.INVISIBLE);
                        ((MainActivity) getContext()).showAfdView();
                        mAirportPressed = null;
                    }
                }
            }
        });


        mPlanButton = (Button) view.findViewById(R.id.location_button_plan);
        mPlanButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(null == mAirportPressed) {
                    return;
                }
                String type = Destination.BASE;
                if(mAirportPressed.contains("&")) {
                    type = Destination.GPS;
                }
                planTo(mAirportPressed, type);
                mAirportPressed = null;
            }
        });

        mDrawClearButton = (Button) view.findViewById(R.id.location_button_draw_clear);
        mDrawClearButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mService != null) {
                    if(mLocationView.getDraw()) {
                        mService.getDraw().clear();
                    }
                }
            }
        });

        mDownloadButton = (Button) view.findViewById(R.id.location_button_dl);
        mDownloadButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getContext(), ChartsDownloadActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(i);
            }
        });

        mPrefButton = (Button) view.findViewById(R.id.location_button_pref);
        mPrefButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Bring up preferences
                 */
                startActivity(new Intent(getContext(), PrefActivity.class));
            }
        });

        mWebButton = (Button) view.findViewById(R.id.location_button_ads);
        mWebButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Bring up preferences
                 */
                startActivity(new Intent(getContext(), MessageActivity.class));
            }
        });

        /*
         * Dest button
         */
        mDestButton = (Button) view.findViewById(R.id.location_button_dest);
        mDestButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                /*
                 * On click, find destination that was pressed on in view
                 */
                if (null == mAirportPressed) {
                    return;
                }
                /*
                 * If button pressed was a destination go there, otherwise if none, then delete current dest
                 */
                String dest = mAirportPressed;
                mAirportPressed = null;
                if (mDestButton.getText().toString().equals(getString(R.string.Delete))) {
                    mService.setDestination(null);
                    mDestLayout.setVisibility(View.INVISIBLE);
                    mLocationView.invalidate();
                    return;
                }
                String type = Destination.BASE;
                if (dest.contains("&")) {
                    type = Destination.GPS;
                }
                goTo(dest, type);
            }
        });


        mSimButton = (TwoButton) view.findViewById(R.id.location_button_sim);
        if (mPref.isSimulationMode()) {
            mSimButton.setText(getString(R.string.SimulationMode));
            mSimButton.setChecked(true);
        }
        else {
            mSimButton.setText(getString(R.string.Navigate));
            mSimButton.setChecked(false);
        }
        mSimButton.setTwoClickListener(new TwoClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Bring up preferences
                 */
                if(mSimButton.getText().equals(getString(R.string.SimulationMode))) {
                    mPref.setSimMode(true);
                    if(null != mService) {
                        Destination dest = mService.getDestination();
                        if(null != dest) {
                            Location l = dest.getLocation();
                            mLocationView.updateParams(new GpsParams(l));
                        }
                        mLocationView.forceReload();
                    }
                }
                else {
                    mPref.setSimMode(false);
                }
            }
        });

        /*
         * Draw
         */
        mDrawButton = (TwoButton) view.findViewById(R.id.location_button_draw);
        mDrawButton.setTwoClickListener(new TwoClickListener() {
            @Override
            public void onClick(View v) {
                /*
                 * Bring up preferences
                 */
                if(mTouchMode == com.ds.avare.touch.Constants.TouchMode.PAN_MODE) {
                    mLocationView.setDraw(true);
                    mTouchMode = com.ds.avare.touch.Constants.TouchMode.DRAW_MODE;
                    mDrawClearButton.setVisibility(View.VISIBLE);
                }
                else {
                    mLocationView.setDraw(false);
                    mTouchMode = com.ds.avare.touch.Constants.TouchMode.PAN_MODE;
                    mDrawClearButton.setVisibility(View.INVISIBLE);
                }
            }
        });

        /*
         * The tracking button handler. Enable/Disable the saving of track points
         * to a KML file
         */
        mTracksButton = (TwoButton) view.findViewById(R.id.location_button_tracks);
        mTracksButton.setTwoClickListener(new TwoClickListener() {
            @Override
            public void onClick(View v) {
                if(null != mService) {
                    setTrackState(!mService.getTracks());
                }
            }
        });


        // The Flight Plan Prev button collection. There are 3, Previous, Pause,
        // and next. They are only visible when a plan has been loaded and
        // activated.

        // Previous - set next destination to the previous waypoint
        mPlanPrev = (Button) view.findViewById(R.id.plan_prev);
        mPlanPrev.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(null != mService) {
                    Plan activePlan = mService.getPlan();
                    if(true == activePlan.isActive()) {
                        activePlan.regress();
                    }
                }
            }
        });

        // Pause - Do no process any waypoint passage logic
        mPlanPause = (ImageButton) view.findViewById(R.id.plan_pause);
        mPlanPause.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(null != mService) {
                    Plan activePlan = mService.getPlan();
                    if(null != activePlan) {
                        mPlanPause.setImageResource(activePlan.suspendResume()
                                ? R.drawable.ic_pause_black_24dp
                                : R.drawable.ic_play_arrow_black_24dp);
                    }
                }
            }
        });

        // Next - advance the destination to the next waypoint
        mPlanNext = (Button) view.findViewById(R.id.plan_next);
        mPlanNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(null != mService) {
                    Plan activePlan = mService.getPlan();
                    if(true == activePlan.isActive()) {
                        activePlan.advance();
                    }
                }
            }
        });

        mAnimateTracks = new AnimateButton(getContext(), mTracksButton, AnimateButton.DIRECTION_R_L, mPlanPrev);
        mAnimateWeb = new AnimateButton(getContext(), mWebButton, AnimateButton.DIRECTION_L_R);
        mAnimateSim = new AnimateButton(getContext(), mSimButton, AnimateButton.DIRECTION_R_L, mPlanNext);
        mAnimateTrack = new AnimateButton(getContext(), mLayerOption, AnimateButton.DIRECTION_R_L, mPlanPause);
        mAnimateChart = new AnimateButton(getContext(), mChartOption, AnimateButton.DIRECTION_R_L, (View[])null);
        mAnimateHelp = new AnimateButton(getContext(), mHelpButton, AnimateButton.DIRECTION_L_R, mCenterButton, mDrawButton, mMenuButton);
        mAnimateDownload = new AnimateButton(getContext(), mDownloadButton, AnimateButton.DIRECTION_L_R, (View[])null);
        mAnimatePref = new AnimateButton(getContext(), mPrefButton, AnimateButton.DIRECTION_L_R, (View[])null);
    }

    private void setTrackState(boolean bState) {
        URI fileURI = mService.setTracks(bState);
        /* The fileURI is returned when the tracks are closed off.
        */
        if(fileURI != null) {
            String fileName = fileURI.getPath().substring((fileURI.getPath().lastIndexOf('/') + 1));
            switch(mPref.autoPostTracks()) {
                case 0:
                    /* Just display a toast message to the user that the file was saved */
                    showSnackbar(String.format(getString(R.string.AutoPostTracksDialogText), fileName), Snackbar.LENGTH_LONG);
                    break;
                case 1:
                    /* Send this file out as an email attachment
                     */
                    try {
                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("application/kml");
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT,
                                getString(R.string.AutoPostTracksSubject) + " " + fileName);
                        emailIntent.putExtra(Intent.EXTRA_STREAM,
                                Uri.fromFile(new File(fileURI.getPath())));
                        startActivity(emailIntent);
                    } catch (Exception e) {

                    }
                    break;

                case 2:
                    /* Send it somewhere as KML. Let the user choose where.
                     */
                    try {
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                        viewIntent.setDataAndType(Uri.fromFile(new File(fileURI.getPath())),
                                "application/vnd.google-earth.kml+xml");
                        startActivity(Intent.createChooser(viewIntent,
                                getString(R.string.AutoPostTracksTitle)));
                    } catch (Exception e) {

                    }
                    break;
            }
        }
    }

    @Override
    public void onToolbarVisibilityChanged(boolean visible) {
        setToolbarAuxButtonsVisibility();
    }

    /**
     * We are interested in events from the fuel tank timer
     * @author Ron
     *
     */
    private class TankObserver implements Observer {

		@Override
		public void update(Observable observable, Object data) {
			final FuelTimer fuelTimer = (FuelTimer) observable;
			switch ((Integer)data) {
				case FuelTimer.REFRESH:
					mLocationView.postInvalidate();
					break;

				case FuelTimer.SWITCH_TANK:
					AlertDialog alertDialog = new AlertDialog.Builder(getContext()).create();
					alertDialog.setTitle(LocationFragment.this.getString(R.string.switchTanks));
					alertDialog.setCancelable(false);
					alertDialog.setCanceledOnTouchOutside(false);
					alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, LocationFragment.this.getString(R.string.OK), new DialogInterface.OnClickListener() {

		                public void onClick(DialogInterface dialog, int which) {
		                    fuelTimer.reset();
		                    dialog.dismiss();
		                }
		            });
					alertDialog.show();
					break;
			}
		}
    }

    /**
     * We are interested in events from the timer
     * @author Ron
     *
     */
    private class TimerObserver implements Observer {

        @Override
        public void update(Observable observable, Object data) {
            final UpTimer upTimer = (UpTimer) observable;
            switch ((Integer)data) {
                case UpTimer.REFRESH:
                    mLocationView.postInvalidate();
                    break;
            }
        }
    }

    /**
     * Set the flight plan buttons visibility
     */
    public void setPlanButtonVis() {
	    int planButtons = View.INVISIBLE;
		if (true == mPref.getPlanControl()) {
	        if (null != mService) {
		        Plan activePlan = mService.getPlan();
		        if (null != activePlan) {
		        	if (true == activePlan.isActive()) {
	        			planButtons = View.VISIBLE;
	        		}
	        	}
	        }
	    }

	    // Set the flight plan button visibility
	    mPlanPrev.setVisibility(planButtons);
	    mPlanPause.setVisibility(planButtons);
	    mPlanNext.setVisibility(planButtons);
	}

    public void setToolbarAuxButtonsVisibility() {
        int visibility = mPref.getHideToolbar() ? View.VISIBLE : View.INVISIBLE;
        mLayerOption.setVisibility(visibility);
        mChartOption.setVisibility(visibility);
        mDrawerButton.setVisibility(visibility);
    }

    @Override
    public void onResume() {
        super.onResume();

        mDestLayout.setVisibility(View.INVISIBLE);

        // Set visibility of the plan buttons
        setPlanButtonVis();
        setToolbarAuxButtonsVisibility();

        if (mService != null) {
            // Tell the fuel tank timer we need to know when it runs out
            mService.getFuelTimer().addObserver(mTankObserver);
            mService.getUpTimer().addObserver(mTimerObserver);
        }

        // Button colors to be synced across activities
        mCenterButton.getBackground().setColorFilter(
                mPref.isTrackUp() ? 0xFF00FF00 : 0xFF444444,
                PorterDuff.Mode.MULTIPLY
        );
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        if (hidden && mService != null) {
            mService.getFlightStatus().unregisterListener(mFSInfc);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mService != null) {
            mService.getFlightStatus().unregisterListener(mFSInfc);
            mService.getFuelTimer().removeObserver(mTankObserver);
            mService.getUpTimer().removeObserver(mTimerObserver);
        }

        // Kill dialogs
        try {
            mAlertDialogDatabase.dismiss();
            mGpsWarnDialog.dismiss();
            mWarnDialog.dismiss();
            mAlertDialogExit.dismiss();
        } catch (Exception e) { }

        // Do this as switching from screen needs to hide its menu
        hideMenu();
    }

    @Override
    public void onGpsLocation(Location location) {
        if (location != null && mService != null) {
            // Called by GPS. Update everything driven by GPS.
            GpsParams params = new GpsParams(location);

            // Store GPS last location in case activity dies, we want to start from same loc
            mLocationView.updateParams(params);

            if (mService != null && mService.getPlan().isEarlyPass() && mPref.shouldBlinkScreen()) {
                // Check that if we are close to passing a plan passage, blink
                blink();
            }
        }
    }

    @Override
    public void onGpsTimeout(boolean timeout) {
        /*
         *  No GPS signal
         *  Tell location view to show GPS status
         */
        if (mService == null) {
            mLocationView.updateErrorStatus(getString(R.string.Init));
        }
        else if(!(new File(mPref.mapsFolder() + "/" + getResources().getStringArray(R.array.resFilesDatabase)[0]).exists())) {
            mLocationView.updateErrorStatus(getString(R.string.DownloadDBShort));
        }
        else if(!(new File(mPref.mapsFolder() + "/tiles")).exists()) {
            mLocationView.updateErrorStatus(getString(R.string.MissingMaps));
        }
        else if(mPref.isSimulationMode()) {
            mLocationView.updateErrorStatus(getString(R.string.SimulationMode));
        }
        else if(timeout) {
            mLocationView.updateErrorStatus(getString(R.string.GPSLost));
        }
        else {
            // GPS kicking.
            mLocationView.updateErrorStatus(null);
        }
    }

    @Override
    public void update(Observable arg0, Object arg1) {
        /*
         * Destination found?
         */
        if(arg0 instanceof Destination) {
            Boolean result = (Boolean)arg1;
            if(result) {
            
                /*
                 * Temporarily move to destination by giving false GPS signal.
                 */
                if(null == mDestination) {
                    showSnackbar(getString(R.string.DestinationNF), Snackbar.LENGTH_SHORT);
                    return;
                }
                if((Destination)arg0 != mDestination) {
                    /*
                     * If user presses a selection repeatedly, reject previous
                     */
                    return;                    
                }
                mPref.addToRecent(mDestination.getStorageName());
                if(!mIsWaypoint) {
                    mLocationView.updateDestination();
                    if(mService != null) {
                        mService.setDestination((Destination)arg0);
                    }
                    showSnackbar(getString(R.string.DestinationSet) + ((Destination)arg0).getID(), Snackbar.LENGTH_SHORT);
                    ((MainActivity) getContext()).showMapView();
                }
                else {
                    if(mService != null) {
                        String snackbarText;
                        if(mService.getPlan().insertDestination((Destination)arg0)) {
                            snackbarText = ((Destination)arg0).getID() + getString(R.string.PlanSet);
                        }
                        else {
                            snackbarText = ((Destination)arg0).getID() + getString(R.string.PlanNoset);
                        }
                        showSnackbar(snackbarText, Snackbar.LENGTH_SHORT);
                    }
                }
                
                /*
                 * Move to new dest temporarily if sim mode.
                 */
                if(mPref.isSimulationMode()) {
                    Location l = mDestination.getLocation();
                    mLocationView.updateParams(new GpsParams(l));
                }
                mLocationView.forceReload();

            }
            else {
                showSnackbar(getString(R.string.DestinationNF), Snackbar.LENGTH_SHORT);
            }
        }
    }
    
    /**
     * Blink screen for an alert
     */
    private void blink() {
        Runnable r = new Runnable() {
            public void run() {
            	/*
            	 * By making the view invisible, background shows
            	 */
                if(mLocationView.getVisibility() == View.VISIBLE) {
                	mLocationView.setVisibility(View.INVISIBLE);
                }
                else {
                	mLocationView.setVisibility(View.VISIBLE);                        	
                }
            }
        };
        
        /*
         * Schedule 10 times
         */
        Handler h = new Handler();
        for(int ms = 500; ms <= 5000; ms+=500) {
        	h.postDelayed(r, ms);
        }
    }

    public void setTracksMode(boolean tracksMode) {
        if (mService != null) {
            setTrackState(tracksMode);
            if (tracksMode) showSnackbar("Tracks enabled", Snackbar.LENGTH_SHORT);
        }
    }

    public void setSimulationMode(boolean simulationMode) {
        mPref.setSimMode(simulationMode);

        if (simulationMode) {
            showSnackbar("Simulation mode enabled", Snackbar.LENGTH_SHORT);

            if (null != mService) {
                Destination dest = mService.getDestination();
                if (null != dest) {
                    Location l = dest.getLocation();
                    mLocationView.updateParams(new GpsParams(l));
                }
                mLocationView.forceReload();
            }
        } else {
            showSnackbar("Simulation mode disabled", Snackbar.LENGTH_SHORT);
        }
    }

    @Override
    protected void postServiceConnected() {
        mService.getFlightStatus().registerListener(mFSInfc);

        // Tell the fuel tank timer we need to know when it runs out
        mService.getFuelTimer().addObserver(mTankObserver);
        mService.getUpTimer().addObserver(mTimerObserver);

        mService.getTiles().setOrientation();

        /*
         * Check if database needs upgrade
         */
        if(!mService.getDBResource().isPresent()) {

            mAlertDialogDatabase = new AlertDialog.Builder(getContext()).create();
            mAlertDialogDatabase.setTitle(getString(R.string.download));
            mAlertDialogDatabase.setCancelable(false);
            mAlertDialogDatabase.setCanceledOnTouchOutside(false);
            mAlertDialogDatabase.setMessage(getString(R.string.DownloadDB));
            mAlertDialogDatabase.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.download), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Intent i = new Intent(getContext(), ChartsDownloadActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    mLocationView.zoomOut();
                    startActivity(i);
                }
            });
            mAlertDialogDatabase.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.Cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            mAlertDialogDatabase.show();
            return;
        }

        /*
         * Now set location if not obtained from service
         */
        mDestination = mService.getDestination();
        if(mPref.isSimulationMode()) {
            // In sim mode, set location to destination or last known location
            if(mDestination != null && mDestination.getLocation() != null) {
                mService.setGpsParams(new GpsParams(mDestination.getLocation()));
            }
            else if (mInitLocation != null) {
                mService.setGpsParams(new GpsParams(mInitLocation));
            }
        }
        else {
            // In navigate mode, leave location to GPS location, or last known location
            if(mService.getGpsParams() == null && mInitLocation != null) {
                mService.setGpsParams(new GpsParams(mInitLocation));
            }
        }

        mLocationView.initParams(mService.getGpsParams(), mService);
        mLocationView.updateParams(mService.getGpsParams());

        /*
         * See if we got an intent to search for address as dest
         */
        if(null != mExtras) {
            String addr = mExtras.getString(Intent.EXTRA_TEXT);
            if(addr != null) {

                /*
                 * , cannot be saved in prefs
                 */
                addr = StringPreference.formatAddressName(addr);

                mDestination = new Destination(addr, Destination.MAPS, mPref, mService);
                mDestination.addObserver(LocationFragment.this);
                showSnackbar(getString(R.string.Searching) + " " + addr, Snackbar.LENGTH_SHORT);
                mDestination.find();
            }
            mExtras = null;
        }

        // mService is now valid, set the plan button vis
        setPlanButtonVis();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.toolbar_location_menu, menu);

        MenuItem chartItem = menu.findItem(R.id.action_chart);
        MenuItem layerItem = menu.findItem(R.id.action_layer);
        MenuItem tracksItem = menu.findItem(R.id.action_tracks);
        MenuItem simulationItem = menu.findItem(R.id.action_simulation);
        MenuItem flightPlanControlsItem = menu.findItem(R.id.action_flight_plan_controls);

        AppCompatSpinner chartSpinner = (AppCompatSpinner) MenuItemCompat.getActionView(chartItem);
        AppCompatSpinner layerSpinner = (AppCompatSpinner) MenuItemCompat.getActionView(layerItem);

        ArrayAdapter<String> chartAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, Boundaries.getChartTypes());
        chartAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chartSpinner.setAdapter(chartAdapter);
        chartSpinner.setSelection(Integer.valueOf(mPref.getChartType()), false);

        chartSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        mPref.setChartType(String.valueOf(position));
                        mChartOption.setCurrentSelectionIndex(position);
                        mLocationView.forceReload();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) { }
                }
        );

        ArrayList<String> layerItems = new ArrayList<>(3);
        layerItems.addAll(Arrays.asList(LocationFragment.LAYER_TYPES));
        final ArrayAdapter<String> layerAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, layerItems);
        layerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        layerSpinner.setAdapter(layerAdapter);
        layerSpinner.setSelection(layerItems.indexOf(mPref.getLayerType()), false);

        layerSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        mPref.setLayerType(layerAdapter.getItem(position));
                        mLayerOption.setSelectedValue(layerAdapter.getItem(position));
                        mLocationView.setLayerType(mPref.getLayerType());
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) { }
                }
        );

        tracksItem.setChecked(mService != null && mService.getTracks());
        simulationItem.setChecked(mPref.isSimulationMode());
        flightPlanControlsItem.setChecked(mPref.getPlanControl());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_tracks:
                item.setChecked(!item.isChecked());
                setTracksMode(item.isChecked());
                break;
            case R.id.action_simulation:
                item.setChecked(!item.isChecked());
                setSimulationMode(item.isChecked());
                break;
            case R.id.action_flight_plan_controls:
                item.setChecked(!item.isChecked());
                mPref.setPlanControl(item.isChecked());
                setPlanButtonVis();
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

}
