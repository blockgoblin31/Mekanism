package mekanism.common.lib.distribution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Keeps track of a target for emitting from various networks.
 *
 * @param <HANDLER> The Handler this target keeps track of.
 * @param <TYPE>    The type that is being transferred.
 * @param <EXTRA>   Any extra information this target may need to keep track of.
 */
public abstract class Target<HANDLER, TYPE extends Number & Comparable<TYPE>, EXTRA> {

    /**
     * Collection of handlers
     */
    protected final Collection<HANDLER> handlers;
    /**
     * Collection of handler type pairs that want more than we can/are willing to provide. Value is the amount they want.
     */
    protected final Collection<HandlerType<HANDLER, TYPE>> needed;

    private int handlerCount = 0;

    protected EXTRA extra;

    protected Target() {
        handlers = new LinkedList<>();
        needed = new LinkedList<>();
    }

    protected Target(Collection<HANDLER> allHandlers) {
        this.handlers = Collections.unmodifiableCollection(allHandlers);
        this.needed = new ArrayList<>(allHandlers.size() / 2);
        this.handlerCount = allHandlers.size();
    }

    protected Target(int expectedSize) {
        this.handlers = new ArrayList<>(expectedSize);
        this.needed = new ArrayList<>(expectedSize / 2);
    }

    public void addHandler(HANDLER handler) {
        handlers.add(handler);
        handlerCount++;
    }

    public int getHandlerCount() {
        return handlerCount;
    }

    /**
     * Sends the remaining amount to each handler we still have not settled on an amount for. We increment the amount sent in splitInfo as well as adjust the split as
     * needed if one ends up accepting less than it originally wanted. (The most likely case this would change is with multi-blocks where it may return the same desire to
     * all connections, but get satisfied by our first connection).
     *
     * @param splitInfo Keeps track of the current amount sent and the default each one can get.
     */
    public void sendRemainingSplit(SplitInfo<TYPE> splitInfo) {
        //If needed is not empty then we default it to the given calculated fair split amount of remaining energy
        if (!needed.isEmpty() && !splitInfo.isZero(splitInfo.getRemainderAmount())) {
            Iterator<HandlerType<HANDLER, TYPE>> iterator = needed.iterator();
            while (iterator.hasNext()) {
                TYPE remainderAmount = splitInfo.getRemainderAmount();
                if (splitInfo.isZero(remainderAmount)) {
                    //We finished inserting everything we wanted to, we can just exit
                    return;
                }
                HandlerType<HANDLER, TYPE> needInfo = iterator.next();
                //Accept the remaining amount
                TYPE amountNeeded = needInfo.amount();
                if (amountNeeded.compareTo(remainderAmount) <= 0) {
                    acceptAmount(needInfo.handler(), splitInfo, amountNeeded);
                    //If the amount we needed was the less than or the same as our remaining amount
                    // we can remove the value as it has now been sent
                    iterator.remove();
                } else {
                    splitInfo.decrementTargets = false;
                    acceptAmount(needInfo.handler(), splitInfo, remainderAmount);
                    splitInfo.decrementTargets = true;
                }
            }
            //TODO: If we remove buffers maybe we should evaluate not caring if we don't actually send the full excess remainder?
            // Given ideally we wouldn't attempting to insert the excess remainder to handlers as a second call to the handler on the same tick
            if (!splitInfo.isZero(splitInfo.getUnsent())) {
                //If we still have some of a remainder after trying to evenly distribute the remainder just send it to the first target willing to accept it
                // This might happen if one of the destinations was only able to accept part of the remaining amount, though in general that case will be
                // covered by shifting the needed values
                for (HandlerType<HANDLER, TYPE> recipient : needed) {
                    TYPE remaining = splitInfo.getUnsent();
                    if (splitInfo.isZero(remaining)) {
                        //We finished, exit
                        return;
                    }
                    acceptAmount(recipient.handler(), splitInfo, remaining);
                }
            }
        }
    }

    /**
     * Gives the handler on the specified side the given amount.
     *
     * @param handler   Handler to give to.
     * @param splitInfo Information about current overall split. The given split will be increased by the actual amount accepted, in case it is less than the offered
     *                  amount.
     * @param amount    Amount to give.
     *
     * @implNote Must call {@link SplitInfo#send(Number)} with the amount actually accepted.
     */
    protected abstract void acceptAmount(HANDLER handler, SplitInfo<TYPE> splitInfo, TYPE amount);

    /**
     * Simulate inserting into the handler.
     *
     * @param handler The handler (should correspond with the side we are simulating).
     * @param extra   All the information we are inserting.
     *
     * @return The amount it was actually willing to accept.
     */
    protected abstract TYPE simulate(HANDLER handler, EXTRA extra);

    /**
     * Calculates how much each handler can take of toSend. If the amount requested is less than the amount per handler/target in splitInfo it immediately sends the
     * requested amount to the handler via {@link #acceptAmount(HANDLER, SplitInfo, Number)}
     *
     * @param toSend    The total amount getting sent.
     * @param splitInfo Information about current overall split.
     */
    public void sendPossible(EXTRA toSend, SplitInfo<TYPE> splitInfo) {
        if (splitInfo.isZero(splitInfo.getShareAmount())) {
            //We are all remainder, just calculate how much each can accept
            for (HANDLER entry : handlers) {
                TYPE amountNeeded = simulate(entry, toSend);
                if (!splitInfo.isZero(amountNeeded)) {
                    needed.add(new HandlerType<>(entry, amountNeeded));
                }
            }
        } else {
            for (HANDLER entry : handlers) {
                TYPE amountNeeded = simulate(entry, toSend);
                if (amountNeeded.compareTo(splitInfo.getShareAmount()) <= 0) {
                    //Add the amount, in case something changed from simulation only mark actual sent amount
                    // in split info
                    if (!splitInfo.isZero(amountNeeded)) {
                        //Note: We can skip actually running it if it doesn't need anything
                        acceptAmount(entry, splitInfo, amountNeeded);
                    }
                } else {
                    needed.add(new HandlerType<>(entry, amountNeeded));
                }
            }
        }
    }

    /**
     * Rechecks to see if any of the needed amounts is able to fit under the new split and if so gives them the requested amount.
     *
     * @param splitInfo The new split to (re)check.
     */
    public void shiftNeeded(SplitInfo<TYPE> splitInfo) {
        if (splitInfo.isZero(splitInfo.getShareAmount())) {
            return;
        }
        Iterator<HandlerType<HANDLER, TYPE>> iterator = needed.iterator();
        //Use an iterator rather than a copy of the keySet of the needed subMap
        // This allows for us to remove it once we find it without  having to
        // start looping again or make a large number of copies of the set
        while (iterator.hasNext()) {
            HandlerType<HANDLER, TYPE> needInfo = iterator.next();
            TYPE amountNeeded = needInfo.amount();
            if (amountNeeded.compareTo(splitInfo.getShareAmount()) <= 0) {
                acceptAmount(needInfo.handler(), splitInfo, amountNeeded);
                //Remove it as it has now been sent
                iterator.remove();
                //Continue checking things in case we happen to be
                // getting things in a bad order so that we don't recheck
                // the same values many times
            }
        }
    }

    protected record HandlerType<HANDLER, TYPE extends Number & Comparable<TYPE>>(HANDLER handler, TYPE amount) {
    }
}