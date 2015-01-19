package myapp.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping( "/*" )
public class MyController {

    @RequestMapping( value = "/", method = GET )
    public String index() {
        return "redirect:/static-content/index.html";
    }

    @RequestMapping( value = "/data", method = GET )
    public @ResponseBody MyData data( Principal principal ) {
        return new MyData( 12345, "OneBigWord" );
    }


}
