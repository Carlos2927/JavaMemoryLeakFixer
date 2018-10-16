package com.carlos2927.java.memoryleakfixer;

/**
 * 由于android的fragment中没有提供好的方式判断fragment是否被销毁,因此提供此接口用于外部判断
 */
public interface FragmentDestroyStateGetter {

    boolean isFragmentDestroyed();
}
