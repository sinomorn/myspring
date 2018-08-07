package com.sinopoc.myspring.controller;

import com.sinopoc.annotation.KAutowired;
import com.sinopoc.annotation.KController;
import com.sinopoc.annotation.KRequestMapping;
import com.sinopoc.myspring.service.ProductService;

@KController
@KRequestMapping("/product")
public class ProductController {

    @KAutowired
    private ProductService productService;

    @KRequestMapping("/query")
    public void queryPro(){

    }
}
