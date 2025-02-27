package de.escalon.hypermedia.sample.store;

import de.escalon.hypermedia.sample.beans.store.OrderedItem;
import de.escalon.hypermedia.sample.model.store.OrderedItemModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.server.mvc.RepresentationModelAssemblerSupport;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Assembles product as ordererdItem.
 *
 * <p>Created by Dietrich on 02.11.2015.
 */
@Component
public class OrderedItemAssembler
    extends RepresentationModelAssemblerSupport<OrderedItemModel, OrderedItem> {

  @Autowired ProductAssembler productAssembler;

  public OrderedItemAssembler() {
    super(OrderedItemController.class, OrderedItem.class);
  }

  @NonNull
  @Override
  public OrderedItem toModel(@NonNull OrderedItemModel entity) {
    return createModelWithId(entity.orderedItemId, entity);
  }

  @NonNull
  @Override
  protected OrderedItem instantiateModel(@NonNull OrderedItemModel entity) {
    OrderedItem orderedItem = super.instantiateModel(entity);
    orderedItem.setOrderedItem(productAssembler.toModel(entity.orderedItem));
    orderedItem.setOrderItemNumber(Integer.toString(entity.orderedItemId));
    return orderedItem;
  }
}
