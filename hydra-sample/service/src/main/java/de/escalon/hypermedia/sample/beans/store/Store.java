package de.escalon.hypermedia.sample.beans.store;

import de.escalon.hypermedia.hydra.mapping.Expose;
import java.util.ArrayList;
import java.util.List;
import org.springframework.hateoas.RepresentationModel;

/**
 * Created by Dietrich on 17.02.2015.
 */
@Expose("CafeOrCoffeeShop")
public class Store extends RepresentationModel<Store> {

    public String name = "Kaffeehaus Hagen";

    private List<Offer> offers = new ArrayList<>();

    public String getName() {
        return name;
    }

    public List<Offer> getMakesOffer() {
        return offers;
    }

    public void addOffer(Offer offer) {
        this.offers.add(offer);
    }

}
