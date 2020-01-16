package com.insightdi.person.services;


import com.insightdi.person.model.Person;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
public class PersonService {

    private static final Log LOGGER = LogFactory.getLog(PersonService.class);



    @Value("${chainURL:unset}")
    private String chainUrl;

    @RequestMapping("/people")
    public List<Person> people(){

        List<Person> people = new ArrayList<>();
        Person person = new Person();
        person.setFirstName("Scott");
        person.setLastName("Morgan");
        people.add(person);
        person = new Person();
        person.setFirstName("Dan");
        person.setLastName("Lange");
        people.add(person);
        person = new Person();
        person.setFirstName("Dan");
        person.setLastName("Putt");
        people.add(person);

        person = new Person();
        person.setFirstName("Susan");
        person.setLastName("Jones");
        people.add(person);

        return people;
    }


    @RequestMapping("/")
    public String health(){

        return "ok";
    }
}
