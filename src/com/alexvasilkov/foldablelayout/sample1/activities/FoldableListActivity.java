package com.alexvasilkov.foldablelayout.sample1.activities;

import android.os.Bundle;

import com.alexvasilkov.foldablelayout.FoldableListLayout;
import com.alexvasilkov.foldablelayout.sample.items.PaintingsAdapter;
import com.alexvasilkov.foldablelayout.sample1.R;
import com.azcltd.fluffycommons.utils.Views;

public class FoldableListActivity extends BaseActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_foldable_list);

		FoldableListLayout foldableListLayout = Views.find(this,
				R.id.foldable_list);
		foldableListLayout.setAdapter(new PaintingsAdapter(this));
	}

}
