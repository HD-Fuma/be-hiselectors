package com.fuma.hiselectors.selectors.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Selector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
}
