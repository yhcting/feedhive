package free.yhc.feeder.model;

import java.util.Iterator;
import java.util.LinkedList;

public class KeyBasedLinkedList<T> {
    private LinkedList<Elem> l = new LinkedList<Elem>();

    private static class Elem {
        final Object key;
        final Object item;
        Elem(Object aKey, Object aItem) {
            key = aKey;
            item = aItem;
        }
    }

    private class Iter implements Iterator<T> {
        Iterator<Elem> itr = l.iterator();

        @Override
        public boolean
        hasNext() {
            return itr.hasNext();
        }

        @Override
        public T
        next() {
            return (T)itr.next().item;
        }

        @Override
        public void
        remove() {
            itr.remove();
        }
    }

    public KeyBasedLinkedList() {
    }

    public boolean
    add(Object key, T item) {
        return l.add(new Elem(key, item));
    }

    public void
    addFirst(Object key, T item) {
        l.addFirst(new Elem(key, item));
    }

    public void
    addLast(Object key, T item) {
        l.addLast(new Elem(key, item));
    }

    public void
    remove(Object key) {
        Iterator<Elem> itr = l.iterator();
        while (itr.hasNext()) {
            Elem e = itr.next();
            if (e.key == key)
                itr.remove();
        }
    }

    public boolean
    remove(Object key, T item) {
        Iterator<Elem> itr = l.iterator();
        while (itr.hasNext()) {
            Elem e = itr.next();
            if (e.key == key && e.item == item) {
                itr.remove();
                return true;
            }
        }
        return false;
    }

    public Iterator<T>
    iterator() {
        return new Iter();
    }

    public T[]
    toArray(T[] a) {
        Elem[] es = l.toArray(new Elem[0]);
        if (a.length < es.length)
            a = (T[])Utils.newArray(a.getClass().getComponentType(), es.length);
        for (int i = 0; i < es.length; i++)
            a[i] = (T)es[i].item;
        return a;
    }
}
