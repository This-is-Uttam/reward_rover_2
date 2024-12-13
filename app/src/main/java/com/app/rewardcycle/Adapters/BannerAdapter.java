package com.app.rewardcycle.Adapters;


import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.rewardcycle.Modals.BannerModal;
import com.app.rewardcycle.R;
import com.app.rewardcycle.databinding.CarouselItemBinding;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;

public class BannerAdapter extends RecyclerView.Adapter<BannerAdapter.Viewholder> {
    ArrayList<BannerModal> bannerList;
    Context context;
    boolean centerCrop = true;


    public BannerAdapter(ArrayList<BannerModal> bannerList, Context context) {
        this.bannerList = bannerList;
        this.context = context;


    }


    @NonNull
    @Override
    public BannerAdapter.Viewholder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.carousel_item,parent, false);
        return new Viewholder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BannerAdapter.Viewholder holder, int position) {
        BannerModal bannerModal = bannerList.get(position);

        Picasso.get()
                .load(bannerModal.getBannerImg())
                .placeholder(R.drawable.placeholder)
                .into(holder.binding.carouselImage);

//        holder.binding.promotionBannerUrl.setText(bannerModal.getBannerImgLink());

        holder.binding.carouselImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent bannerIntent = new Intent(Intent.ACTION_VIEW);
                Uri uri = Uri.parse(bannerModal.getBannerImgLink());
                bannerIntent.setData(uri);
                context.startActivity(bannerIntent);
            }
        });


    }

    @Override
    public int getItemCount() {
        return bannerList.size();
    }

    public class Viewholder extends RecyclerView.ViewHolder {
//        PromotionRvItemBinding binding;
        CarouselItemBinding binding;
        public Viewholder(@NonNull View itemView) {
            super(itemView);
//            promotionImg = itemView.findViewById(R.id.promotionImg);
            binding = CarouselItemBinding.bind(itemView);
//            binding = PromotionRvItemBinding.bind(itemView);
        }

    }
}
