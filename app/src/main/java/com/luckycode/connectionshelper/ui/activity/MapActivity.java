package com.luckycode.connectionshelper.ui.activity;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.j256.ormlite.dao.Dao;
import com.luckycode.connectionshelper.interactor.MapInteractor;
import com.luckycode.connectionshelper.model.Edge;
import com.luckycode.connectionshelper.model.Graph;
import com.luckycode.connectionshelper.model.Town;
import com.luckycode.connectionshelper.model.TownVertex;
import com.luckycode.connectionshelper.ui.adapter.PlaceAutocompleteAdapter.PlaceAutocomplete;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.model.LatLngBounds;
import com.luckycode.connectionshelper.R;
import com.luckycode.connectionshelper.common.LuckyActivity;
import com.luckycode.connectionshelper.presenter.MapPresenter;
import com.luckycode.connectionshelper.ui.adapter.PlaceAutocompleteAdapter;
import com.luckycode.connectionshelper.ui.viewModel.MapActivityView;
import com.luckycode.connectionshelper.utils.KeyboardHelper;

import java.sql.SQLException;

import butterknife.BindView;
import butterknife.OnClick;

public class MapActivity extends LuckyActivity implements PlaceAutocompleteAdapter.PlaceAutoCompleteInterface, GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks,OnMapReadyCallback, TextWatcher,MapActivityView{

    @BindView(R.id.list_search)RecyclerView mRecyclerView;
    @BindView(R.id.search_et)AutoCompleteTextView mSearchEdittext;
    @BindView(R.id.clear)ImageView mClear;
    @BindView(R.id.menu_more)ImageButton menuMore;
    private Graph graph;
    private MapPresenter mPresenter;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private PlaceAutocompleteAdapter mAdapter;
    private static final LatLngBounds BOUNDS_INDIA = new LatLngBounds(new LatLng(-0, 0), new LatLng(0, 0));

    @Override
    protected void init() {
        Bundle bundle= getIntent().getExtras();
        graph= (Graph) bundle.getSerializable("GRAPH");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, 0 , this)
                .addApi(Places.GEO_DATA_API)
                .build();

        mPresenter=new MapPresenter(this,getHelper(),graph);

        setRecyclerView();
        mSearchEdittext.addTextChangedListener(this);
    }

    public void setRecyclerView(){
        LinearLayoutManager linearlm=new LinearLayoutManager(this);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(linearlm);
        mAdapter=new PlaceAutocompleteAdapter(this,mGoogleApiClient,BOUNDS_INDIA,null);
        mRecyclerView.setAdapter(mAdapter);
    }

    public void drawRoute(Edge edge){
        PolylineOptions rectOptions = new PolylineOptions()
                .add(new LatLng(edge.getOrigin().getLat(),edge.getOrigin().getLng()))
                .add(new LatLng(edge.getDestination().getLat(),edge.getDestination().getLng()))
                .color(Color.RED);
        mMap.addPolyline(rectOptions);
    }

    @Override
    public void onPlaceClick(PlaceAutocomplete placeAutocomplete){
        mPresenter.handlePlaceClick(placeAutocomplete,mGoogleApiClient);
    }

    public void drawInitMap(){
        for(Edge edge:graph.getEdges())
            drawRoute(edge);
        for(TownVertex vertex:graph.getVertexes())
            drawMarker(vertex);
    }

    private void drawMarker(TownVertex vertex) {
        LatLng latLng=new LatLng(vertex.getLat(),vertex.getLng());
        Marker marker=mMap.addMarker(new MarkerOptions().position(latLng).
        title(vertex.getName()));
        marker.showInfoWindow();
    }

    @Override
    public void updateMap(TownVertex town) {
        if(mMap != null){
            mMap.clear();
            LatLng latLng = new LatLng(town.getLat(),town.getLng());
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 5));
            drawInitMap();
            KeyboardHelper.hideKeyboard(MapActivity.this);
        }
    }

    @Override
    protected int getLayout() {
        return R.layout.activity_map;
    }

    @Override
    protected Class getFragmentToAdd() {
        return null;
    }

    @Override
    protected int getFragmentLayout() {
        return R.id.search_layout;
    }


    @Override
    public void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    public void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @OnClick(R.id.clear)
    public void onClick(View view){
        mSearchEdittext.setText("");
        if(mAdapter!=null)
            mAdapter.clearList();
    }

    @OnClick(R.id.menu_more)
    public void onMenuMoreClicked(View view){
        PopupMenu popup = new PopupMenu(this, view);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.popup_menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.menu_settings:
                        showSettingsDialog();
                        return true;
                    default:
                        return false;
                }
            }
        });
        popup.show();
    }

    public void showSettingsDialog(){
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_settings_custom);
        dialog.show();
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
        drawInitMap();
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mPresenter.onTextChanged(s,count);
    }

    @Override
    public void setAdapter(){
        if(mAdapter!=null){
            mRecyclerView.setAdapter(mAdapter);
            showRecyclerView();
        }
    }

    @Override
    public void filter(String s){
        if(mGoogleApiClient.isConnected())
            mAdapter.getFilter().filter(s);
    }

    @Override
    public void afterTextChanged(Editable editable) {

    }

    @Override
    public void showClearButton() {
        mClear.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideClearButton() {
        mClear.setVisibility(View.GONE);
    }

    @Override
    public void showRecyclerView() {
        mRecyclerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void hideRecyclerView() {
        mRecyclerView.setVisibility(View.GONE);
    }
}


