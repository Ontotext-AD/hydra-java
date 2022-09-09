package de.escalon.hypermedia.sample.store;


import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import de.escalon.hypermedia.sample.beans.store.Order;
import de.escalon.hypermedia.sample.beans.store.Product;
import de.escalon.hypermedia.sample.model.store.OrderModel;
import de.escalon.hypermedia.sample.model.store.OrderedItemModel;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
/**
 * Created by Dietrich on 17.02.2015.
 */
@Component
public class OrderAssembler extends RepresentationModelAssemblerSupport<OrderModel, Order> {

    @Autowired
    private ProductAssembler itemAssembler;

    public OrderAssembler() {
        super(OrderController.class, Order.class);
    }

    @NonNull
    @Override
    public Order toModel(@NonNull OrderModel entity) {
        return createModelWithId(entity.getId(), entity);
    }

    @NonNull
    @Override
    protected Order instantiateModel(@NonNull OrderModel entity) {
        Order order = super.instantiateModel(entity);
        order.setOrderStatus(entity.getOrderStatus());
        List<OrderedItemModel> orderedItems = entity.getOrderedItems();
        for (OrderedItemModel orderedItemModel : orderedItems) {
            Product product = itemAssembler.instantiateModel(orderedItemModel.orderedItem);
            product.add(linkTo(OrderedItemController.class, entity.getId())
                    .slash(orderedItemModel.orderedItemId)
                    .withSelfRel());
            order.addItem(product);
        }
        return order;
    }
}
